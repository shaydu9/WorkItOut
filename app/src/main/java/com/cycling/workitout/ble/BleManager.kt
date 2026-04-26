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

    private val mockDataScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mockDataGenerator = MockDataGenerator(mockDataScope)

    // BLE allows only one in-flight write at a time — all control writes go through this queue.
    private val bleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val controlWriteQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private var pendingWriteAck: CompletableDeferred<Boolean>? = null

    // FE-C needs periodic resends to keep ERG locked — re-send at 1 Hz (3 Hz burst on target changes).
    private var currentFecTargetWatts: Int? = null
    private var fecResendJob: kotlinx.coroutines.Job? = null
    private val FEC_RESEND_INTERVAL_MS = 1_000L
    private val FEC_BURST_INTERVAL_MS = 300L
    private val FEC_BURST_DURATION_MS = 3_000L
    @Volatile private var fecBurstUntilMs: Long = 0L

    // High-frequency logs are gated here — only useful during an active ride.
    private val _workoutActive = MutableStateFlow(false)
    fun setWorkoutActive(active: Boolean) {
        if (_workoutActive.value == active) return
        _workoutActive.value = active
        Timber.i(if (active) "Workout started — verbose BLE logging ON"
                  else         "Workout stopped — verbose BLE logging OFF")
    }
    private inline fun sampleLog(block: () -> String) {
        if (_workoutActive.value) Timber.d(block())
    }

    init {
        bleScope.launch {
            for (data in controlWriteQueue) {
                when (_controlMode.value) {
                    ControlMode.FTMS -> doFtmsWrite(data)
                    ControlMode.FEC  -> doFecWrite(data)
                    ControlMode.NONE -> Timber.w("Control write dropped — no trainer control mode")
                }
            }
        }
    }
    
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) {
            Timber.d( "Already scanning")
            return
        }
        
        if (!isBluetoothEnabled()) {
            Timber.e( "Bluetooth is not enabled")
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
        Timber.d( "Started BLE scan")
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Timber.d( "Stopped BLE scan")
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
                Timber.d( "Discovered device: $deviceName ($deviceType)")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Timber.e( "Scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun connectHeartRateMonitor(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.HEART_RATE_MONITOR) {
            Timber.e( "Device is not a heart rate monitor")
            return
        }
        
        heartRateGatt?.close()
        heartRateGatt = bleDevice.device.connectGatt(context, false, heartRateGattCallback)
        Timber.d( "Connecting to heart rate monitor: ${bleDevice.name}")
    }
    
    @SuppressLint("MissingPermission")
    fun connectPowerMeter(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.POWER_METER) {
            Timber.e( "Device is not a power meter")
            return
        }
        
        powerMeterGatt?.close()
        powerMeterGatt = bleDevice.device.connectGatt(context, false, powerMeterGattCallback)
        Timber.d( "Connecting to power meter: ${bleDevice.name}")
    }
    
    @SuppressLint("MissingPermission")
    fun reconnectHeartRateMonitor(macAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                heartRateGatt?.close()
                heartRateGatt = device.connectGatt(context, false, heartRateGattCallback)
                Timber.d( "Reconnecting to heart rate monitor: $macAddress")
            } else {
                Timber.e( "Bluetooth adapter not available")
            }
        } catch (e: IllegalArgumentException) {
            Timber.e( "Invalid MAC address: $macAddress", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun reconnectPowerMeter(macAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                powerMeterGatt?.close()
                powerMeterGatt = device.connectGatt(context, false, powerMeterGattCallback)
                Timber.d( "Reconnecting to power meter: $macAddress")
            } else {
                Timber.e( "Bluetooth adapter not available")
            }
        } catch (e: IllegalArgumentException) {
            Timber.e( "Invalid MAC address: $macAddress", e)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun disconnectHeartRateMonitor() {
        heartRateGatt?.disconnect()
        heartRateGatt?.close()
        heartRateGatt = null
        _isHeartRateConnected.value = false
        Timber.d( "Disconnected heart rate monitor")
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
        Timber.d( "Disconnected power meter")
    }
    
    @SuppressLint("MissingPermission")
    fun connectTrainer(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.SMART_TRAINER) {
            Timber.e("Device is not a smart trainer")
            return
        }

        trainerGatt?.close()
        trainerGatt = bleDevice.device.connectGatt(context, false, trainerGattCallback)
        Timber.d("Connecting to smart trainer: ${bleDevice.name}")
    }

    @SuppressLint("MissingPermission")
    fun reconnectTrainer(macAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            if (device != null) {
                trainerGatt?.close()
                trainerGatt = device.connectGatt(context, false, trainerGattCallback)
                Timber.d("Reconnecting to smart trainer: $macAddress")
            } else {
                Timber.e("Bluetooth adapter not available")
            }
        } catch (e: IllegalArgumentException) {
            Timber.e("Invalid MAC address: $macAddress", e)
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
        Timber.d("Disconnected smart trainer")
    }

    // FE-C doesn't need a control handshake — Page 49 frames are accepted directly.
    fun requestFtmsControl() {
        when (_controlMode.value) {
            ControlMode.FTMS -> enqueueControlWrite(byteArrayOf(BleConstants.FTMS_REQUEST_CONTROL))
            ControlMode.FEC  -> Timber.d("FE-C control: requestControl is implicit — skipping")
            ControlMode.NONE -> Timber.w("requestFtmsControl dropped — no control mode")
        }
    }

    // FE-C starts ERG on first Page 49 — no explicit start needed.
    fun startFtmsWorkout() {
        when (_controlMode.value) {
            ControlMode.FTMS -> enqueueControlWrite(byteArrayOf(BleConstants.FTMS_START_RESUME))
            ControlMode.FEC  -> Timber.d("FE-C control: start is implicit — skipping")
            ControlMode.NONE -> Timber.w("startFtmsWorkout dropped — no control mode")
        }
    }

    // FE-C: send 0 W to release resistance and let the trainer coast.
    fun stopFtmsWorkout() {
        when (_controlMode.value) {
            ControlMode.FTMS -> enqueueControlWrite(byteArrayOf(BleConstants.FTMS_STOP_PAUSE, 0x01)) // 0x01 = Stop
            ControlMode.FEC  -> {
                stopFecResender()
                currentFecTargetWatts = null
                enqueueControlWrite(TacxFecClient.buildTargetPowerFrame(0))
                Timber.d("FE-C control: stopped resend and sent Page 49 target=0W")
            }
            ControlMode.NONE -> Timber.w("stopFtmsWorkout dropped — no control mode")
        }
    }

    fun setTargetPower(watts: Int) {
        when (_controlMode.value) {
            ControlMode.FTMS -> {
                val data = byteArrayOf(
                    BleConstants.FTMS_SET_TARGET_POWER,
                    (watts and 0xFF).toByte(),
                    ((watts shr 8) and 0xFF).toByte()
                )
                enqueueControlWrite(data)
                Timber.d("FTMS setTargetPower: $watts W")
            }
            ControlMode.FEC -> {
                val frame = TacxFecClient.buildTargetPowerFrame(watts)
                enqueueControlWrite(frame)
                currentFecTargetWatts = watts
                // Burst window: resend at 3 Hz for a few seconds so the trainer latches faster.
                fecBurstUntilMs = System.currentTimeMillis() + FEC_BURST_DURATION_MS
                ensureFecResender()
                Timber.d("TacxFec setTargetPower: $watts W (Page 49, ${frame.size}B) — burst for ${FEC_BURST_DURATION_MS}ms")
            }
            ControlMode.NONE -> Timber.w("setTargetPower($watts) dropped — no control mode")
        }
    }

    fun setDemoTargetPower(watts: Int) {
        if (_isDemoMode.value) {
            mockDataGenerator.setTargetPower(watts)
        }
    }

    private fun enqueueControlWrite(data: ByteArray) {
        when (_controlMode.value) {
            ControlMode.FTMS -> if (ftmsControlPointChar == null || trainerGatt == null) {
                Timber.w("FTMS control point not available — dropping opcode 0x${data[0].toString(16).padStart(2,'0')}")
                return
            }
            ControlMode.FEC -> if (fecWriteChar == null || trainerGatt == null) {
                Timber.w("FE-C write char not available — dropping frame")
                return
            }
            ControlMode.NONE -> {
                Timber.w("No control mode — dropping write")
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
            Timber.w("FTMS writeCharacteristic returned false for opcode 0x${data[0].toString(16).padStart(2,'0')}")
            pendingWriteAck = null
            return
        }
        val result = withTimeoutOrNull(2_000) { ack.await() } ?: false
        sampleLog { "FTMS write opcode 0x${data[0].toString(16).padStart(2,'0')} ACK=$result" }
        pendingWriteAck = null
    }

    // Keeps sending the last target so the trainer stays locked — burst=3 Hz, steady=1 Hz.
    private fun ensureFecResender() {
        if (fecResendJob?.isActive == true) return
        fecResendJob = bleScope.launch {
            while (_controlMode.value == ControlMode.FEC) {
                val now = System.currentTimeMillis()
                val interval = if (now < fecBurstUntilMs) FEC_BURST_INTERVAL_MS else FEC_RESEND_INTERVAL_MS
                kotlinx.coroutines.delay(interval)
                val target = currentFecTargetWatts ?: continue
                if (fecWriteChar == null || trainerGatt == null) break
                val frame = TacxFecClient.buildTargetPowerFrame(target)
                controlWriteQueue.trySend(frame)
                sampleLog { "TacxFec resend: $target W (interval=${interval}ms)" }
            }
        }
    }

    private fun stopFecResender() {
        fecResendJob?.cancel()
        fecResendJob = null
    }

    // Write-with-response so the trainer ACKs — gives us flow control for free.
    @SuppressLint("MissingPermission")
    private suspend fun doFecWrite(data: ByteArray) {
        val char = fecWriteChar ?: return
        val gatt = trainerGatt ?: return
        val ack = CompletableDeferred<Boolean>()
        pendingWriteAck = ack
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        char.value = data
        val queued = gatt.writeCharacteristic(char)
        if (!queued) {
            Timber.w("FE-C writeCharacteristic returned false")
            pendingWriteAck = null
            return
        }
        val result = withTimeoutOrNull(2_000) { ack.await() } ?: false
        sampleLog { "FE-C write ACK=$result (${data.size}B)" }
        pendingWriteAck = null
    }

    fun setPowerSmoothingWindow(seconds: Int) {
        powerSmoother.setSmoothingWindow(seconds)
        Timber.d( "Power smoothing window set to $seconds seconds")
    }
    
    fun getPowerSmoothingWindow(): Int {
        return powerSmoother.getSmoothingWindow()
    }
    
    fun enableDemoMode() {
        if (_isDemoMode.value) return
        Timber.d( "Enabling demo mode")
        _isDemoMode.value = true
        _isHeartRateConnected.value = true
        _isPowerMeterConnected.value = true
        _isTrainerConnected.value = true
        _trainerControlAvailable.value = true
        mockDataGenerator.start()
        mockDataScope.launch {
            mockDataGenerator.heartRateData.collect { mockHr ->
                if (_isDemoMode.value) _heartRateData.value = mockHr
            }
        }
        mockDataScope.launch {
            mockDataGenerator.powerData.collect { mockPower ->
                if (_isDemoMode.value) {
                    val smoothedPower = powerSmoother.addReading(mockPower.power)
                    _powerData.value = PowerData(smoothedPower, mockPower.cadence)
                }
            }
        }
    }

    fun disableDemoMode() {
        if (!_isDemoMode.value) return
        Timber.d( "Disabling demo mode")
        _isDemoMode.value = false
        mockDataGenerator.stop()
        _isHeartRateConnected.value = false
        _isPowerMeterConnected.value = false
        _isTrainerConnected.value = false
        _trainerControlAvailable.value = false
        mockDataGenerator.clearTargetPower()
        _heartRateData.value = HeartRateData(0)
        _powerData.value = PowerData(0, 0)
        powerSmoother.clear()
    }
    
    fun getDemoPhaseDescription(): String {
        return if (_isDemoMode.value) {
            mockDataGenerator.getCurrentPhaseDescription()
        } else {
            ""
        }
    }
    
    fun getDemoElapsedTime(): String {
        return if (_isDemoMode.value) {
            mockDataGenerator.getElapsedTimeFormatted()
        } else {
            "00:00"
        }
    }
    
    fun getDemoElapsedSeconds(): Int {
        return if (_isDemoMode.value) {
            mockDataGenerator.getElapsedSeconds()
        } else {
            0
        }
    }
    
    private val heartRateGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d( "Heart rate monitor connected")
                    _isHeartRateConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d( "Heart rate monitor disconnected")
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
                    
                    Timber.d( "Enabled heart rate notifications")
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
                    Timber.d( "Power meter connected")
                    _isPowerMeterConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d( "Power meter disconnected")
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
                    
                    Timber.d( "Enabled power meter notifications")
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
                    Timber.d("Smart trainer connected")
                    _isTrainerConnected.value = true
                    // Trainers are also the power/cadence source — flag so the UI shows power as connected.
                    _isPowerMeterConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Smart trainer disconnected")
                    _isTrainerConnected.value = false
                    _isPowerMeterConnected.value = false
                    _trainerControlAvailable.value = false
                    ftmsControlPointChar = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Trainer service discovery failed with status $status")
                return
            }

            val allServices = gatt.services.map { it.uuid.toString() }
            Timber.i("Trainer services discovered (${allServices.size}): $allServices")

            val ftmsService = gatt.getService(BleConstants.FITNESS_MACHINE_SERVICE_UUID)
            val cpsService = gatt.getService(BleConstants.CYCLING_POWER_SERVICE_UUID)

            if (ftmsService != null) {
                Timber.i("FTMS service found — using Indoor Bike Data + Control Point")
                _controlMode.value = ControlMode.FTMS

                ftmsService.getCharacteristic(BleConstants.INDOOR_BIKE_DATA_CHAR_UUID)?.let { char ->
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Timber.d("Enabled Indoor Bike Data notifications")
                }

                ftmsService.getCharacteristic(BleConstants.FTMS_CONTROL_POINT_CHAR_UUID)?.let { char ->
                    ftmsControlPointChar = char
                    _trainerControlAvailable.value = true
                    gatt.setCharacteristicNotification(char, true)
                    Timber.d("FTMS Control Point cached, indications pending")
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
                        Timber.i("Tacx FE-C over BLE detected — ERG via ANT+ Page 49 on ${writeChar.uuid}")
                    } else {
                        Timber.w("Tacx FE-C service found but write char (6e40fec3) missing")
                    }
                } else {
                    Timber.w("FTMS not found and no Tacx FE-C service — read-only (no ERG control)")
                }

                // Regardless of control path, subscribe to CPS for power + cadence.
                if (cpsService != null) {
                    cpsService.getCharacteristic(BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID)?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Timber.d("Enabled Cycling Power notifications (CPS data path)")
                    }
                    // CSC subscription chains via onDescriptorWrite below.
                } else {
                    Timber.e("Trainer exposes neither FTMS nor CPS — power/cadence unavailable")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Timber.d("Trainer descriptor write: char=${descriptor.characteristic.uuid}, status=$status")

            if (descriptor.characteristic.uuid == BleConstants.INDOOR_BIKE_DATA_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                ftmsControlPointChar?.let { cpChar ->
                    val cpDescriptor = cpChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    cpDescriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    val written = gatt.writeDescriptor(cpDescriptor)
                    Timber.d("Writing FTMS Control Point indication descriptor: $written")
                }
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
            when (characteristic.uuid) {
                BleConstants.FTMS_CONTROL_POINT_CHAR_UUID -> {
                    val success = status == BluetoothGatt.GATT_SUCCESS
                    sampleLog { "FTMS onCharacteristicWrite status=$status success=$success" }
                    pendingWriteAck?.complete(success)
                }
                BleConstants.TACX_FEC_WRITE_CHAR_UUID -> {
                    val success = status == BluetoothGatt.GATT_SUCCESS
                    sampleLog { "FE-C onCharacteristicWrite status=$status success=$success" }
                    pendingWriteAck?.complete(success)
                }
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
                        Timber.d("FTMS Control Point response: opcode=0x${value[0].toString(16)}")
                    }
                }
                BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID -> {
                    // Fallback: trainer without FTMS, reading power from CPS
                    val (power, cadence) = parsePowerMeasurement(characteristic)
                    val smoothedPower = powerSmoother.addReading(power)
                    val effectiveCadence = if (cadence > 0) cadence else lastKnownCadence
                    _powerData.value = PowerData(smoothedPower, effectiveCadence)
                    sampleLog { "Trainer CPS fallback - Power: $power W (smoothed: $smoothedPower), Cadence: $effectiveCadence rpm (raw CPS: $cadence)" }
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
            Timber.w( "Power measurement data too short: ${value.size} bytes")
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
                        Timber.w( "Cadence out of range: $cadence, resetting")
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
    
    @SuppressLint("MissingPermission")
    private fun subscribeToCSC(gatt: BluetoothGatt) {
        val cscService = gatt.getService(BleConstants.CYCLING_SPEED_CADENCE_SERVICE_UUID)
        if (cscService == null) {
            Timber.d("CSC service (0x1816) not found on trainer — cadence from CPS only")
            return
        }

        val cscChar = cscService.getCharacteristic(BleConstants.CSC_MEASUREMENT_CHAR_UUID)
        if (cscChar == null) {
            Timber.w("CSC Measurement characteristic not found")
            return
        }

        gatt.setCharacteristicNotification(cscChar, true)
        val descriptor = cscChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val written = gatt.writeDescriptor(descriptor)
        Timber.i("Subscribed to CSC Measurement for cadence: written=$written")
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
