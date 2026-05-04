package com.cycling.workitout.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.ParcelUuid
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.HeartRateData
import com.cycling.workitout.data.PowerData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class BleManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var heartRateGatt: BluetoothGatt? = null
    private var powerMeterGatt: BluetoothGatt? = null
    private var trainerGatt: BluetoothGatt? = null
    private var trainerMacAddress: String? = null  // retained for GATT_ERROR retry
    private var ftmsControlPointChar: BluetoothGattCharacteristic? = null
    // Used when the trainer speaks Tacx FE-C over BLE instead of FTMS.
    private var fecWriteChar: BluetoothGattCharacteristic? = null

    // FTMS = standard Fitness Machine Service; FEC = Tacx proprietary fallback; NONE = read-only
    enum class ControlMode { NONE, FTMS, FEC }
    private val _controlMode = MutableStateFlow(ControlMode.NONE)
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _isHeartRateConnected = MutableStateFlow(false)
    val isHeartRateConnected: StateFlow<Boolean> = _isHeartRateConnected.asStateFlow()

    private val _isPowerMeterConnected = MutableStateFlow(false)
    val isPowerMeterConnected: StateFlow<Boolean> = _isPowerMeterConnected.asStateFlow()

    private val _isTrainerConnected = MutableStateFlow(false)
    val isTrainerConnected: StateFlow<Boolean> = _isTrainerConnected.asStateFlow()

    private val _trainerControlAvailable = MutableStateFlow(false)
    val trainerControlAvailable: StateFlow<Boolean> = _trainerControlAvailable.asStateFlow()

    private val _heartRateData = MutableStateFlow(HeartRateData())
    val heartRateData: StateFlow<HeartRateData> = _heartRateData.asStateFlow()

    private val _powerData = MutableStateFlow(PowerData())
    val powerData: StateFlow<PowerData> = _powerData.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val powerSmoother = PowerSmoother(smoothingWindowSeconds = 3)

    // BLE allows only one in-flight write at a time — all control writes go through this queue.
    // Conflated: if the consumer is mid-write and another frame is queued, only the LATEST is kept.
    // This stops resends from piling up if the trainer's BLE radio briefly stalls.
    private val bleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val controlWriteQueue = Channel<ByteArray>(capacity = Channel.CONFLATED)
    private var pendingWriteAck: CompletableDeferred<Boolean>? = null

    // FE-C: trainer drops ERG if it doesn't see Page 49 for ~4-5s, so we resend at 3 Hz steady.
    // At 333ms cadence even ~10 consecutive frame drops stays under the trainer's tolerance.
    private var currentFecTargetWatts: Int? = null
    private var fecResendJob: kotlinx.coroutines.Job? = null
    private val FEC_RESEND_INTERVAL_MS = 333L

    // FE-C write health counters for the periodic diagnostic line.
    @Volatile private var fecWriteOkCount = 0
    @Volatile private var fecWriteFailCount = 0
    private var fecHealthJob: kotlinx.coroutines.Job? = null

    // ── ERG ownership ───────────────────────────────────────────────────────
    // Only one caller at a time may drive trainer resistance. acquireErgControl()
    // returns an opaque token; the caller must pass it to every ERG-related write.
    // A second acquire invalidates the first — defends against leaked WorkoutViewModels
    // (e.g. duplicated nav back-stack entries) racing to set conflicting targets.
    private val ergOwnerLock = Any()
    @Volatile private var ergOwnerToken: Any? = null

    /** Take exclusive ERG control. Tears down any previous owner's resender. */
    fun acquireErgControl(): Any {
        synchronized(ergOwnerLock) {
            val previous = ergOwnerToken
            val token = Any()
            ergOwnerToken = token
            if (previous != null) {
                // If you see this WARN in a normal ride log, two WorkoutViewModels were alive
                // at the same moment — that's the dual-owner bug this token defends against.
                Timber.tag("ERG").w(
                    "⚠️ control preempted: previous owner=${System.identityHashCode(previous)} " +
                    "evicted by new owner=${System.identityHashCode(token)} — old resender stopped"
                )
                stopFtmsWorkoutInternal()
            } else {
                Timber.tag("ERG").d("control acquired (token=${System.identityHashCode(token)})")
            }
            return token
        }
    }

    /** Release ERG control if [token] still holds it. Idempotent and safe to call after preemption. */
    fun releaseErgControl(token: Any) {
        synchronized(ergOwnerLock) {
            if (ergOwnerToken === token) {
                stopFtmsWorkoutInternal()
                ergOwnerToken = null
                Timber.tag("ERG").d("control released (token=${System.identityHashCode(token)})")
            }
        }
    }

    private fun isCurrentOwner(token: Any?): Boolean {
        if (token == null) return false
        synchronized(ergOwnerLock) { return ergOwnerToken === token }
    }

    // setWorkoutActive is kept for symmetry with the WorkoutEngine lifecycle, but per-sample
    // BLE callbacks no longer log — they fire several times per second per sensor and drown
    // out everything else. Power, HR and cadence still flow through the StateFlows; only the
    // Timber spam is gone. Connection lifecycle, errors, and target writes still log normally.
    private val _workoutActive = MutableStateFlow(false)
    fun setWorkoutActive(active: Boolean) {
        if (_workoutActive.value == active) return
        _workoutActive.value = active
    }
    @Suppress("UNUSED_PARAMETER")
    private inline fun sampleLog(block: () -> String) { /* no-op */ }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val label = when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS              -> "LOSS (app fully lost focus — call started)"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT    -> "LOSS_TRANSIENT (brief interruption)"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK (duck volume)"
            AudioManager.AUDIOFOCUS_GAIN              -> "GAIN (focus returned)"
            else                                      -> "unknown($focusChange)"
        }
        Timber.tag("AUDIO").d("Audio focus change: $label")
    }
    private val audioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
    } else null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            Timber.tag("AUDIO").d("Audio focus requested: result=${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "DENIED($result)"}")
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            Timber.tag("AUDIO").d("Audio focus requested (legacy): result=${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "GRANTED" else "DENIED($result)"}")
        }

        bleScope.launch {
            for (data in controlWriteQueue) {
                when (_controlMode.value) {
                    ControlMode.FTMS -> doFtmsWrite(data)
                    ControlMode.FEC  -> doFecWrite(data)
                    ControlMode.NONE -> Timber.tag("ERG").w("Control write dropped — no trainer control mode")
                }
            }
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) {
            Timber.tag("BLE").d( "Already scanning")
            return
        }
        
        if (!isBluetoothEnabled()) {
            Timber.tag("BLE").e( "Bluetooth is not enabled")
            return
        }
        
        _discoveredDevices.value = emptyList()
        
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.HEART_RATE_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.CYCLING_POWER_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.FITNESS_MACHINE_SERVICE_UUID))
                .build()
        )
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        _isScanning.value = true
        Timber.tag("BLE").d( "Started BLE scan")
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Timber.tag("BLE").d( "Stopped BLE scan")
    }
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address
            val rssi = result.rssi

            // Many trainers only advertise Cycling Power — FTMS appears only after connecting.
            // Match known trainer names too so we can classify them upfront.
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
            val nameLower = deviceName.lowercase()
            val hasFtms = BleConstants.FITNESS_MACHINE_SERVICE_UUID in serviceUuids
            val hasCps = BleConstants.CYCLING_POWER_SERVICE_UUID in serviceUuids
            val hasHr = BleConstants.HEART_RATE_SERVICE_UUID in serviceUuids

            val looksLikeTrainer = KNOWN_TRAINER_KEYWORDS.any { it in nameLower }

            val deviceType = when {
                hasFtms -> DeviceType.SMART_TRAINER
                hasCps && looksLikeTrainer -> DeviceType.SMART_TRAINER
                hasHr -> DeviceType.HEART_RATE_MONITOR
                hasCps -> DeviceType.POWER_METER
                else -> DeviceType.UNKNOWN
            }
            
            val bleDevice = BleDevice(device, deviceName, deviceAddress, rssi, deviceType)

            val currentDevices = _discoveredDevices.value.toMutableList()
            if (currentDevices.none { it.address == deviceAddress }) {
                currentDevices.add(bleDevice)
                _discoveredDevices.value = currentDevices
                Timber.tag("BLE").d( "Discovered device: $deviceName ($deviceType)")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Timber.tag("BLE").e( "Scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun connectHeartRateMonitor(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.HEART_RATE_MONITOR) {
            Timber.tag("BLE").e( "Device is not a heart rate monitor")
            return
        }
        
        heartRateGatt?.close()
        heartRateGatt = bleDevice.device.connectGatt(context, false, heartRateGattCallback)
        Timber.tag("BLE").d( "Connecting to heart rate monitor: ${bleDevice.name}")
    }
    
    @SuppressLint("MissingPermission")
    fun connectPowerMeter(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.POWER_METER) {
            Timber.tag("BLE").e( "Device is not a power meter")
            return
        }
        
        powerMeterGatt?.close()
        powerMeterGatt = bleDevice.device.connectGatt(context, false, powerMeterGattCallback)
        Timber.tag("BLE").d( "Connecting to power meter: ${bleDevice.name}")
    }
    
    @SuppressLint("MissingPermission")
    fun reconnectHeartRateMonitor(macAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                heartRateGatt?.close()
                heartRateGatt = device.connectGatt(context, false, heartRateGattCallback)
                Timber.tag("BLE").d( "Reconnecting to heart rate monitor: $macAddress")
            } else {
                Timber.tag("BLE").e( "Bluetooth adapter not available")
            }
        } catch (e: IllegalArgumentException) {
            Timber.tag("BLE").e( "Invalid MAC address: $macAddress", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun reconnectPowerMeter(macAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                powerMeterGatt?.close()
                powerMeterGatt = device.connectGatt(context, false, powerMeterGattCallback)
                Timber.tag("BLE").d( "Reconnecting to power meter: $macAddress")
            } else {
                Timber.tag("BLE").e( "Bluetooth adapter not available")
            }
        } catch (e: IllegalArgumentException) {
            Timber.tag("BLE").e( "Invalid MAC address: $macAddress", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun disconnectHeartRateMonitor() {
        heartRateGatt?.disconnect()
        heartRateGatt?.close()
        heartRateGatt = null
        _isHeartRateConnected.value = false
        Timber.tag("BLE").d( "Disconnected heart rate monitor")
    }
    
    @SuppressLint("MissingPermission")
    fun disconnectPowerMeter() {
        powerMeterGatt?.disconnect()
        powerMeterGatt?.close()
        powerMeterGatt = null
        _isPowerMeterConnected.value = false
        isFirstCrankMeasurement = true
        previousCrankRevolutions = 0
        previousCrankEventTime = 0
        powerSmoother.clear()
        Timber.tag("BLE").d( "Disconnected power meter")
    }
    
    @SuppressLint("MissingPermission")
    fun connectTrainer(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.SMART_TRAINER) {
            Timber.tag("BLE").e("Device is not a smart trainer")
            return
        }

        trainerMacAddress = bleDevice.device.address
        trainerGatt?.close()
        trainerGatt = bleDevice.device.connectGatt(context, false, trainerGattCallback)
        Timber.tag("BLE").d("Connecting to smart trainer: ${bleDevice.name}")
    }

    @SuppressLint("MissingPermission")
    fun reconnectTrainer(macAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                trainerMacAddress = macAddress
                trainerGatt?.close()
                trainerGatt = device.connectGatt(context, false, trainerGattCallback)
                Timber.tag("BLE").d("Reconnecting to smart trainer: $macAddress")
            } else {
                Timber.tag("BLE").e("Bluetooth adapter not available")
            }
        } catch (e: IllegalArgumentException) {
            Timber.tag("BLE").e("Invalid MAC address: $macAddress", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectTrainer() {
        trainerGatt?.disconnect()
        trainerGatt?.close()
        trainerGatt = null
        ftmsControlPointChar = null
        fecWriteChar = null
        stopFecResender()
        currentFecTargetWatts = null
        _controlMode.value = ControlMode.NONE
        _isTrainerConnected.value = false
        _trainerControlAvailable.value = false
        isFirstCscCrankMeasurement = true
        previousCscCrankRevolutions = 0
        previousCscCrankEventTime = 0
        lastKnownCadence = 0
        cadenceStaleCounter = 0
        isFirstCrankMeasurement = true
        previousCrankRevolutions = 0
        previousCrankEventTime = 0
        powerSmoother.clear()
        Timber.tag("BLE").d("Disconnected smart trainer")
    }

    // FE-C doesn't need a control handshake — Page 49 frames are accepted directly.
    fun requestFtmsControl(token: Any) {
        if (!isCurrentOwner(token)) {
            Timber.tag("ERG").w("requestFtmsControl dropped — caller token=${System.identityHashCode(token)} not current owner=${System.identityHashCode(ergOwnerToken)}")
            return
        }
        when (_controlMode.value) {
            ControlMode.FTMS -> enqueueControlWrite(byteArrayOf(BleConstants.FTMS_REQUEST_CONTROL))
            ControlMode.FEC  -> Timber.tag("ERG").d("FE-C control: requestControl is implicit — skipping")
            ControlMode.NONE -> Timber.tag("ERG").w("requestFtmsControl dropped — no control mode")
        }
    }

    // FE-C starts ERG on first Page 49 — no explicit start needed.
    fun startFtmsWorkout(token: Any) {
        if (!isCurrentOwner(token)) {
            Timber.tag("ERG").w("startFtmsWorkout dropped — caller token=${System.identityHashCode(token)} not current owner=${System.identityHashCode(ergOwnerToken)}")
            return
        }
        when (_controlMode.value) {
            ControlMode.FTMS -> enqueueControlWrite(byteArrayOf(BleConstants.FTMS_START_RESUME))
            ControlMode.FEC  -> Timber.tag("ERG").d("FE-C control: start is implicit — skipping")
            ControlMode.NONE -> Timber.tag("ERG").w("startFtmsWorkout dropped — no control mode")
        }
    }

    // FE-C: send 0 W to release resistance and let the trainer coast.
    fun stopFtmsWorkout(token: Any) {
        if (!isCurrentOwner(token)) {
            Timber.tag("ERG").w("stopFtmsWorkout dropped — caller token=${System.identityHashCode(token)} not current owner=${System.identityHashCode(ergOwnerToken)}")
            return
        }
        stopFtmsWorkoutInternal()
    }

    // Internal: bypasses ownership check. Called by acquireErgControl() to evict a stale
    // owner's resender, by releaseErgControl(), and (legitimately) by stopFtmsWorkout(token).
    private fun stopFtmsWorkoutInternal() {
        when (_controlMode.value) {
            ControlMode.FTMS -> enqueueControlWrite(byteArrayOf(BleConstants.FTMS_STOP_PAUSE, 0x01)) // 0x01 = Stop
            ControlMode.FEC  -> {
                stopFecResender()
                currentFecTargetWatts = null
                enqueueControlWrite(TacxFecClient.buildTargetPowerFrame(0))
                Timber.tag("ERG").d("FE-C control: stopped resend and sent Page 49 target=0W")
            }
            ControlMode.NONE -> Timber.tag("ERG").w("stopFtmsWorkout dropped — no control mode")
        }
    }

    fun setTargetPower(token: Any, watts: Int) {
        if (!isCurrentOwner(token)) {
            Timber.tag("ERG").w("setTargetPower($watts) dropped — caller token=${System.identityHashCode(token)} not current owner=${System.identityHashCode(ergOwnerToken)}")
            return
        }
        when (_controlMode.value) {
            ControlMode.FTMS -> {
                val data = byteArrayOf(
                    BleConstants.FTMS_SET_TARGET_POWER,
                    (watts and 0xFF).toByte(),
                    ((watts shr 8) and 0xFF).toByte()
                )
                enqueueControlWrite(data)
                Timber.tag("ERG").d("FTMS setTargetPower: $watts W")
            }
            ControlMode.FEC -> {
                currentFecTargetWatts = watts
                val frame = TacxFecClient.buildTargetPowerFrame(watts)
                enqueueControlWrite(frame)
                ensureFecResender()
                Timber.tag("ERG").d("TacxFec setTargetPower: $watts W (Page 49, ${frame.size}B) — steady 3 Hz")
            }
            ControlMode.NONE -> Timber.tag("ERG").w("setTargetPower($watts) dropped — no control mode")
        }
    }

    private fun enqueueControlWrite(data: ByteArray) {
        when (_controlMode.value) {
            ControlMode.FTMS -> if (ftmsControlPointChar == null || trainerGatt == null) {
                Timber.tag("ERG").w("FTMS control point not available — dropping opcode 0x${data[0].toString(16).padStart(2,'0')}")
                return
            }
            ControlMode.FEC -> if (fecWriteChar == null || trainerGatt == null) {
                Timber.tag("ERG").w("FE-C write char not available — dropping frame")
                return
            }
            ControlMode.NONE -> {
                Timber.tag("ERG").w("No control mode — dropping write")
                return
            }
        }
        controlWriteQueue.trySend(data)
    }

    @SuppressLint("MissingPermission")
    private suspend fun doFtmsWrite(data: ByteArray) {
        val char = ftmsControlPointChar ?: return
        val gatt = trainerGatt ?: return
        val ack = CompletableDeferred<Boolean>()
        pendingWriteAck = ack
        char.value = data
        val queued = gatt.writeCharacteristic(char)
        if (!queued) {
            Timber.tag("ERG").w("FTMS writeCharacteristic returned false for opcode 0x${data[0].toString(16).padStart(2,'0')}")
            pendingWriteAck = null
            return
        }
        val result = withTimeoutOrNull(2_000) { ack.await() } ?: false
        sampleLog { "FTMS write opcode 0x${data[0].toString(16).padStart(2,'0')} ACK=$result" }
        pendingWriteAck = null
    }

    // Keeps sending the last target so the trainer stays locked — steady 3 Hz.
    // Conflated queue means resend frames silently drop if the consumer is still draining.
    private fun ensureFecResender() {
        if (fecResendJob?.isActive == true) return
        fecResendJob = bleScope.launch {
            while (_controlMode.value == ControlMode.FEC) {
                kotlinx.coroutines.delay(FEC_RESEND_INTERVAL_MS)
                val target = currentFecTargetWatts ?: continue
                if (fecWriteChar == null || trainerGatt == null) break
                val frame = TacxFecClient.buildTargetPowerFrame(target)
                controlWriteQueue.trySend(frame)
            }
        }
        ensureFecHealthLogger()
    }

    private fun stopFecResender() {
        fecResendJob?.cancel()
        fecResendJob = null
        fecHealthJob?.cancel()
        fecHealthJob = null
    }

    // 30s health summary so we have evidence in the log if writes start failing.
    private fun ensureFecHealthLogger() {
        if (fecHealthJob?.isActive == true) return
        fecHealthJob = bleScope.launch {
            var prevOk = 0
            var prevFail = 0
            while (_controlMode.value == ControlMode.FEC) {
                kotlinx.coroutines.delay(30_000L)
                val ok = fecWriteOkCount
                val fail = fecWriteFailCount
                val dOk = ok - prevOk
                val dFail = fail - prevFail
                prevOk = ok; prevFail = fail
                Timber.tag("ERG").i("FE-C health (30s): writes ok=$dOk fail=$dFail target=${currentFecTargetWatts}W")
            }
        }
    }

    // Write-without-response: ANT+ Page 49 is fire-and-forget by design, and the BLE-layer ACK
    // we used to await was the cause of the head-of-line stall that caused mid-workout ERG drops.
    // No-response writes complete in ~ms; if the BLE stack is full, writeCharacteristic returns
    // false and we rely on the next 333ms resend tick to retry.
    @SuppressLint("MissingPermission")
    private suspend fun doFecWrite(data: ByteArray) {
        val char = fecWriteChar ?: return
        val gatt = trainerGatt ?: return
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        char.value = data
        val queued = gatt.writeCharacteristic(char)
        if (queued) fecWriteOkCount++ else fecWriteFailCount++
    }

    fun setPowerSmoothingWindow(seconds: Int) {
        powerSmoother.setSmoothingWindow(seconds)
        Timber.tag("BLE").d( "Power smoothing window set to $seconds seconds")
    }
    
    fun getPowerSmoothingWindow(): Int {
        return powerSmoother.getSmoothingWindow()
    }
    
    private val heartRateGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.tag("BLE").d("Heart rate monitor connected (status=$status)")
                    _isHeartRateConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.tag("BLE").w("Heart rate monitor disconnected (status=$status)")
                    _isHeartRateConnected.value = false
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BleConstants.HEART_RATE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID)
                
                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    
                    val descriptor = it.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    
                    Timber.tag("BLE").d( "Enabled heart rate notifications")
                }
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID) {
                val heartRate = parseHeartRate(characteristic)
                _heartRateData.value = HeartRateData(heartRate)
                sampleLog { "Heart rate: $heartRate bpm" }
            }
        }
    }
    
    private val powerMeterGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.tag("BLE").d("Power meter connected (status=$status)")
                    _isPowerMeterConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.tag("BLE").w("Power meter disconnected (status=$status)")
                    _isPowerMeterConnected.value = false
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BleConstants.CYCLING_POWER_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID)
                
                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    
                    val descriptor = it.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    
                    Timber.tag("BLE").d( "Enabled power meter notifications")
                }
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID) {
                val (power, cadence) = parsePowerMeasurement(characteristic)
                _powerData.value = PowerData(power, cadence)
                sampleLog { "Power: $power W, Cadence: $cadence rpm" }
            }
        }
    }
    
    private val trainerGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.tag("BLE").d("Smart trainer connected")
                    _isTrainerConnected.value = true
                    // Trainers are also the power/cadence source — flag so the UI shows power as connected.
                    _isPowerMeterConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // Decode the GATT status so logs unambiguously distinguish a real RF drop
                    // from app-side issues. status=0 means we initiated the disconnect; non-zero
                    // is the radio reporting why the link died.
                    val reason = when (status) {
                        BluetoothGatt.GATT_SUCCESS -> "clean (app-initiated)"
                        8 -> "8 (LMP_RESPONSE_TIMEOUT — RF interference / out of range)"
                        19 -> "19 (REMOTE_USER_TERMINATED — trainer powered off)"
                        22 -> "22 (LOCAL_HOST_TERMINATED — Android stack closed link)"
                        133 -> "133 (GATT_ERROR — generic radio failure, often range)"
                        else -> "$status"
                    }
                    Timber.tag("BLE").w("Smart trainer disconnected: status=$reason")
                    _isTrainerConnected.value = false
                    _isPowerMeterConnected.value = false
                    _trainerControlAvailable.value = false
                    ftmsControlPointChar = null
                    fecWriteChar = null
                    stopFecResender()
                    _controlMode.value = ControlMode.NONE
                    // currentFecTargetWatts intentionally retained: if reconnect happens during a
                    // workout, the VM's onTargetPowerChanged hook re-pushes the right value.

                    // status=133 (GATT_ERROR) typically means the BLE stack has a stale handle
                    // from a previous process session. Close the old handle, wait for the stack
                    // to release it, then retry once with a fresh connectGatt call.
                    if (status == 133) {
                        val mac = trainerMacAddress
                        if (mac != null) {
                            bleScope.launch {
                                Timber.tag("BLE").i("Trainer GATT_ERROR — retrying in 1s (mac=$mac)")
                                gatt.close()
                                kotlinx.coroutines.delay(1_000L)
                                reconnectTrainer(mac)
                            }
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.tag("BLE").e("Trainer service discovery failed with status $status")
                return
            }

            val allServices = gatt.services.map { it.uuid.toString() }
            Timber.tag("BLE").i("Trainer services discovered (${allServices.size}): $allServices")

            val ftmsService = gatt.getService(BleConstants.FITNESS_MACHINE_SERVICE_UUID)
            val cpsService = gatt.getService(BleConstants.CYCLING_POWER_SERVICE_UUID)

            if (ftmsService != null) {
                Timber.tag("BLE").i("FTMS service found — using Indoor Bike Data + Control Point")
                _controlMode.value = ControlMode.FTMS

                ftmsService.getCharacteristic(BleConstants.INDOOR_BIKE_DATA_CHAR_UUID)?.let { char ->
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Timber.tag("BLE").d("Enabled Indoor Bike Data notifications")
                }

                ftmsService.getCharacteristic(BleConstants.FTMS_CONTROL_POINT_CHAR_UUID)?.let { char ->
                    ftmsControlPointChar = char
                    _trainerControlAvailable.value = true
                    gatt.setCharacteristicNotification(char, true)
                    Timber.tag("ERG").d("FTMS Control Point cached, indications pending")
                }
            } else {
                // No FTMS — try Tacx FE-C over BLE; power/cadence still come from CPS.
                val fecService = gatt.getService(BleConstants.TACX_FEC_SERVICE_UUID)
                if (fecService != null) {
                    val writeChar = fecService.getCharacteristic(BleConstants.TACX_FEC_WRITE_CHAR_UUID)
                    if (writeChar != null) {
                        fecWriteChar = writeChar
                        _controlMode.value = ControlMode.FEC
                        _trainerControlAvailable.value = true
                        Timber.tag("ERG").i("Tacx FE-C over BLE detected — ERG via ANT+ Page 49 on ${writeChar.uuid}")
                    } else {
                        Timber.tag("ERG").w("Tacx FE-C service found but write char (6e40fec3) missing")
                    }
                } else {
                    Timber.tag("ERG").w("FTMS not found and no Tacx FE-C service — read-only (no ERG control)")
                }

                // Regardless of control path, subscribe to CPS for power + cadence.
                if (cpsService != null) {
                    cpsService.getCharacteristic(BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID)?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Timber.tag("BLE").d("Enabled Cycling Power notifications (CPS data path)")
                    }
                    // CSC subscription chains via onDescriptorWrite below.
                } else {
                    Timber.tag("BLE").e("Trainer exposes neither FTMS nor CPS — power/cadence unavailable")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Timber.tag("BLE").d("Trainer descriptor write: char=${descriptor.characteristic.uuid}, status=$status")

            if (descriptor.characteristic.uuid == BleConstants.INDOOR_BIKE_DATA_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                if (ftmsControlPointChar != null) {
                    val cpChar = ftmsControlPointChar!!
                    val cpDescriptor = cpChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    cpDescriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    val written = gatt.writeDescriptor(cpDescriptor)
                    Timber.tag("ERG").d("Writing FTMS Control Point indication descriptor: $written")
                } else {
                    // No Control Point — go straight to CPS cadence fallback for FTMS trainers
                    // that omit cadence from Indoor Bike Data (e.g. Wahoo KICKR).
                    subscribeToCpsForCadence(gatt)
                }
            }

            if (descriptor.characteristic.uuid == BleConstants.FTMS_CONTROL_POINT_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                // After FTMS control point is set up, subscribe to CPS if available — some FTMS
                // trainers (e.g. Wahoo KICKR) don't include cadence in Indoor Bike Data.
                subscribeToCpsForCadence(gatt)
            }

            // BLE allows one descriptor write at a time — chain CSC after CPS completes.
            if (descriptor.characteristic.uuid == BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                subscribeToCSC(gatt)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // FE-C uses WRITE_TYPE_NO_RESPONSE so this callback only fires for FTMS now.
            if (characteristic.uuid == BleConstants.FTMS_CONTROL_POINT_CHAR_UUID) {
                val success = status == BluetoothGatt.GATT_SUCCESS
                sampleLog { "FTMS onCharacteristicWrite status=$status success=$success" }
                pendingWriteAck?.complete(success)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                BleConstants.INDOOR_BIKE_DATA_CHAR_UUID -> {
                    parseIndoorBikeData(characteristic)
                }
                BleConstants.FTMS_CONTROL_POINT_CHAR_UUID -> {
                    val value = characteristic.value ?: return
                    if (value.isNotEmpty()) {
                        Timber.tag("ERG").d("FTMS Control Point response: opcode=0x${value[0].toString(16)}")
                    }
                }
                BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID -> {
                    val (power, cadence) = parsePowerMeasurement(characteristic)
                    if (_controlMode.value == ControlMode.FTMS) {
                        // FTMS trainer (e.g. Wahoo KICKR) that omits cadence from Indoor Bike Data —
                        // use CPS cadence only; power already comes from Indoor Bike Data.
                        if (cadence > 0) {
                            val currentPower = _powerData.value.power
                            _powerData.value = PowerData(currentPower, cadence)
                            sampleLog { "FTMS+CPS cadence supplement: $cadence rpm" }
                        }
                    } else {
                        // Non-FTMS trainer: CPS is the primary power+cadence source.
                        val effectiveCadence = if (cadence > 0) cadence else lastKnownCadence
                        _powerData.value = PowerData(power, effectiveCadence)
                        sampleLog { "Trainer CPS fallback - Power: $power W, Cadence: $effectiveCadence rpm (raw CPS: $cadence)" }
                    }
                }
                BleConstants.CSC_MEASUREMENT_CHAR_UUID -> {
                    val cadence = parseCscMeasurement(characteristic)
                    if (cadence >= 0) {
                        lastKnownCadence = cadence
                        val currentPower = _powerData.value.power
                        _powerData.value = PowerData(currentPower, cadence)
                        sampleLog { "Trainer CSC cadence: $cadence rpm" }
                    }
                }
            }
        }
    }

    // Parses the FTMS Indoor Bike Data characteristic (0x2AD2) — each field is flag-gated.
    private fun parseIndoorBikeData(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value ?: return
        if (value.size < 2) return

        val flags = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
        var offset = 2

        if (flags and 0x01 == 0 && value.size >= offset + 2) offset += 2 // speed
        if (flags and 0x02 != 0 && value.size >= offset + 2) offset += 2 // avg speed

        var cadence = 0
        if (flags and 0x04 != 0 && value.size >= offset + 2) {
            val rawCadence = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            cadence = rawCadence / 2
            offset += 2
        }

        if (flags and 0x08 != 0 && value.size >= offset + 2) offset += 2 // avg cadence
        if (flags and 0x10 != 0 && value.size >= offset + 3) offset += 3 // distance (uint24)
        if (flags and 0x20 != 0 && value.size >= offset + 2) offset += 2 // resistance

        var power = 0
        if (flags and 0x40 != 0 && value.size >= offset + 2) {
            power = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            if (power > 32767) power -= 65536
            power = power.coerceAtLeast(0)
            offset += 2
        }

        if (flags and 0x80 != 0 && value.size >= offset + 2) offset += 2 // avg power

        if (power > 0 || cadence > 0) {
            val smoothedPower = powerSmoother.addReading(power)
            _powerData.value = PowerData(smoothedPower, cadence)
            sampleLog { "Indoor Bike Data - Power: $power W (smoothed: $smoothedPower), Cadence: $cadence RPM" }
        }

        if (flags and 0x0200 != 0 && value.size > offset) {
            if (flags and 0x0100 != 0 && value.size >= offset + 5) offset += 5 // expended energy
            if (value.size > offset) {
                val heartRate = value[offset].toInt() and 0xFF
                if (heartRate > 0) _heartRateData.value = HeartRateData(heartRate)
            }
        }
    }

    // Flag byte 0 bit 0: 0 = HR is uint8, 1 = HR is uint16.
    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int {
        val value = characteristic.value ?: return 0
        val format = value[0].toInt() and 0x01
        return if (format == 0) value[1].toInt() and 0xFF
        else ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
    }
    
    private var previousCrankRevolutions: Int = 0
    private var previousCrankEventTime: Int = 0
    private var isFirstCrankMeasurement = true

    private var previousCscCrankRevolutions: Int = 0
    private var previousCscCrankEventTime: Int = 0
    private var isFirstCscCrankMeasurement = true

    // Held between packets; decays to 0 after ~5 missing measurements.
    private var lastKnownCadence: Int = 0
    private var cadenceStaleCounter: Int = 0

    // Parses the Cycling Power Service characteristic — power is sint16, cadence from crank delta.
    private fun parsePowerMeasurement(characteristic: BluetoothGattCharacteristic): Pair<Int, Int> {
        val value = characteristic.value ?: return Pair(0, 0)

        if (value.size < 4) {
            Timber.tag("BLE").w( "Power measurement data too short: ${value.size} bytes")
            return Pair(0, 0)
        }

        val flags = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
        val power = (value[2].toInt() and 0xFF) or ((value[3].toInt() and 0xFF) shl 8)

        sampleLog { "Power flags: 0x${flags.toString(16)}, Power: $power W, Data size: ${value.size} bytes" }

        var cadence = 0
        var offset = 4

        if (flags and 0x01 != 0 && value.size > offset) offset += 1
        if (flags and 0x04 != 0 && value.size >= offset + 2) offset += 2
        if (flags and 0x10 != 0 && value.size >= offset + 6) offset += 6

        // Bit 5: crank revolution data — this is where cadence lives.
        if (flags and 0x20 != 0 && value.size >= offset + 4) {
            val crankRevolutions = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            val crankEventTime = (value[offset + 2].toInt() and 0xFF) or ((value[offset + 3].toInt() and 0xFF) shl 8)

            if (!isFirstCrankMeasurement) {
                var revolutionsDelta = crankRevolutions - previousCrankRevolutions
                var timeDelta = crankEventTime - previousCrankEventTime
                if (revolutionsDelta < 0) revolutionsDelta += 65536
                if (timeDelta < 0) timeDelta += 65536
                if (timeDelta > 0) {
                    cadence = ((revolutionsDelta * 60 * 1024) / timeDelta).toInt()
                    if (cadence < 0 || cadence > 250) {
                        Timber.tag("BLE").w( "Cadence out of range: $cadence, resetting")
                        cadence = 0
                    }
                }
            } else {
                isFirstCrankMeasurement = false
            }

            previousCrankRevolutions = crankRevolutions
            previousCrankEventTime = crankEventTime

            if (cadence > 0) {
                lastKnownCadence = cadence
                cadenceStaleCounter = 0
            }
            sampleLog { "Crank data - Revolutions: $crankRevolutions, Time: $crankEventTime, Cadence: $cadence RPM" }
        } else {
            // No crank data — decay last known cadence to zero after ~5 missing measurements.
            cadenceStaleCounter++
            if (cadenceStaleCounter > 5) lastKnownCadence = 0
            cadence = lastKnownCadence
            sampleLog { "No crank data in CPS (flags: 0x${flags.toString(16)}), using lastKnownCadence=$cadence" }
        }

        val smoothedPower = powerSmoother.addReading(power)
        sampleLog { "Raw power: $power W, Smoothed power: $smoothedPower W (${powerSmoother.getSmoothingWindow()}s average)" }

        return Pair(smoothedPower, cadence)
    }
    
    // Subscribes to CPS for cadence supplementation on FTMS trainers that omit cadence from
    // Indoor Bike Data (e.g. Wahoo KICKR). No-op if CPS service is not present.
    @SuppressLint("MissingPermission")
    private fun subscribeToCpsForCadence(gatt: BluetoothGatt) {
        val cpsService = gatt.getService(BleConstants.CYCLING_POWER_SERVICE_UUID) ?: return
        val cpsChar = cpsService.getCharacteristic(BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID) ?: return
        gatt.setCharacteristicNotification(cpsChar, true)
        val descriptor = cpsChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val written = gatt.writeDescriptor(descriptor)
        Timber.tag("BLE").i("FTMS+CPS cadence fallback: subscribed to CPS for cadence, written=$written")
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToCSC(gatt: BluetoothGatt) {
        val cscService = gatt.getService(BleConstants.CYCLING_SPEED_CADENCE_SERVICE_UUID)
        if (cscService == null) {
            Timber.tag("BLE").d("CSC service (0x1816) not found on trainer — cadence from CPS only")
            return
        }

        val cscChar = cscService.getCharacteristic(BleConstants.CSC_MEASUREMENT_CHAR_UUID)
        if (cscChar == null) {
            Timber.tag("BLE").w("CSC Measurement characteristic not found")
            return
        }

        gatt.setCharacteristicNotification(cscChar, true)
        val descriptor = cscChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val written = gatt.writeDescriptor(descriptor)
        Timber.tag("BLE").i("Subscribed to CSC Measurement for cadence: written=$written")
    }

    // Returns cadence in RPM from the CSC characteristic, or -1 if not yet calculable.
    private fun parseCscMeasurement(characteristic: BluetoothGattCharacteristic): Int {
        val value = characteristic.value ?: return -1
        if (value.isEmpty()) return -1

        val flags = value[0].toInt() and 0xFF
        var offset = 1

        if (flags and 0x01 != 0) offset += 6 // skip wheel revolution data

        if (flags and 0x02 != 0 && value.size >= offset + 4) {
            val crankRevolutions = (value[offset].toInt() and 0xFF) or
                    ((value[offset + 1].toInt() and 0xFF) shl 8)
            val crankEventTime = (value[offset + 2].toInt() and 0xFF) or
                    ((value[offset + 3].toInt() and 0xFF) shl 8)

            if (isFirstCscCrankMeasurement) {
                isFirstCscCrankMeasurement = false
                previousCscCrankRevolutions = crankRevolutions
                previousCscCrankEventTime = crankEventTime
                return -1
            }

            var revDelta = crankRevolutions - previousCscCrankRevolutions
            var timeDelta = crankEventTime - previousCscCrankEventTime
            if (revDelta < 0) revDelta += 65536
            if (timeDelta < 0) timeDelta += 65536

            previousCscCrankRevolutions = crankRevolutions
            previousCscCrankEventTime = crankEventTime

            if (timeDelta > 0) {
                // Crank event time is in 1/1024 s units.
                val cadence = ((revDelta * 60 * 1024) / timeDelta)
                return if (cadence in 0..250) cadence else 0
            }
            return 0
        }

        return -1
    }

    @SuppressLint("MissingPermission")
    fun cleanup() {
        stopScan()
        disconnectHeartRateMonitor()
        disconnectPowerMeter()
        disconnectTrainer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        Timber.tag("AUDIO").d("Audio focus released (cleanup)")
    }
}

// Known trainer name fragments — used to classify devices that only advertise CPS (not FTMS) during scan.
private val KNOWN_TRAINER_KEYWORDS = listOf(
    "tacx",         // Tacx Neo, Flux, Vortex, etc.
    "kickr",        // Wahoo KICKR, KICKR Core, KICKR Snap
    "direto",       // Elite Direto
    "suito",        // Elite Suito
    "zumo",         // Elite Zumo
    "flux",         // Tacx Flux (also caught by "tacx")
    "hammer",       // Saris Hammer / H3
    "saris h",      // Saris H3, H2
    "zwift hub",    // Zwift Hub
    "jetblack",     // JetBlack trainers
    "magene t",     // Magene T100/T300 trainers (not power meters)
    "noza",         // Xplova Noza
    "bushido",      // Tacx Bushido
    "genius",       // Tacx Genius
    "drivo",        // Elite Drivo
    "kura",         // Elite Kura
    "bkool",        // Bkool trainers
    "kinetic",      // Kinetic smart trainers
    "trainer",      // Generic fallback for devices with "trainer" in the name
)
