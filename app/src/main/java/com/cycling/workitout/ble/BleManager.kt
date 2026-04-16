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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Central BLE manager for scanning, connecting, and managing cycling sensors
 */
class BleManager(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var heartRateGatt: BluetoothGatt? = null
    private var powerMeterGatt: BluetoothGatt? = null
    private var trainerGatt: BluetoothGatt? = null
    private var ftmsControlPointChar: BluetoothGattCharacteristic? = null
    
    // State flows for discovered devices
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()
    
    // State flows for connection status
    private val _isHeartRateConnected = MutableStateFlow(false)
    val isHeartRateConnected: StateFlow<Boolean> = _isHeartRateConnected.asStateFlow()
    
    private val _isPowerMeterConnected = MutableStateFlow(false)
    val isPowerMeterConnected: StateFlow<Boolean> = _isPowerMeterConnected.asStateFlow()

    private val _isTrainerConnected = MutableStateFlow(false)
    val isTrainerConnected: StateFlow<Boolean> = _isTrainerConnected.asStateFlow()

    private val _trainerControlAvailable = MutableStateFlow(false)
    val trainerControlAvailable: StateFlow<Boolean> = _trainerControlAvailable.asStateFlow()
    
    // State flows for sensor data
    private val _heartRateData = MutableStateFlow(HeartRateData())
    val heartRateData: StateFlow<HeartRateData> = _heartRateData.asStateFlow()
    
    private val _powerData = MutableStateFlow(PowerData())
    val powerData: StateFlow<PowerData> = _powerData.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // Power smoothing
    private val powerSmoother = PowerSmoother(smoothingWindowSeconds = 3)
    
    // Demo mode with mock data
    private val mockDataScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mockDataGenerator = MockDataGenerator(mockDataScope)
    
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()
    
    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Start scanning for cycling BLE devices
     */
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
    
    /**
     * Stop scanning for BLE devices
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Timber.d( "Stopped BLE scan")
    }
    
    /**
     * Scan callback for discovered devices
     */
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address
            val rssi = result.rssi
            
            // Determine device type based on advertised services + device name.
            //
            // Many smart trainers (Tacx Neo 2T, Wahoo KICKR, etc.) only advertise
            // the Cycling Power service UUID in their BLE advertisement packets.
            // FTMS is only discoverable *after* connecting and doing service
            // discovery. So we can't rely solely on service UUIDs — we also match
            // known trainer names to classify them as SMART_TRAINER up front.
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
            
            // Add device if not already in the list
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
    
    /**
     * Connect to a heart rate monitor
     */
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
    
    /**
     * Connect to a power meter
     */
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
    
    /**
     * Reconnect to a heart rate monitor using MAC address
     */
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
    
    /**
     * Reconnect to a power meter using MAC address
     */
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
    
    /**
     * Disconnect heart rate monitor
     */
    @SuppressLint("MissingPermission")
    fun disconnectHeartRateMonitor() {
        heartRateGatt?.disconnect()
        heartRateGatt?.close()
        heartRateGatt = null
        _isHeartRateConnected.value = false
        Timber.d( "Disconnected heart rate monitor")
    }
    
    /**
     * Disconnect power meter
     */
    @SuppressLint("MissingPermission")
    fun disconnectPowerMeter() {
        powerMeterGatt?.disconnect()
        powerMeterGatt?.close()
        powerMeterGatt = null
        _isPowerMeterConnected.value = false
        
        // Reset crank data for next connection
        isFirstCrankMeasurement = true
        previousCrankRevolutions = 0
        previousCrankEventTime = 0
        
        // Clear power smoothing data
        powerSmoother.clear()
        
        Timber.d( "Disconnected power meter")
    }
    
    /**
     * Connect to a smart trainer (FTMS)
     */
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

    /**
     * Reconnect to a smart trainer using MAC address
     */
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

    /**
     * Disconnect smart trainer
     */
    @SuppressLint("MissingPermission")
    fun disconnectTrainer() {
        trainerGatt?.disconnect()
        trainerGatt?.close()
        trainerGatt = null
        ftmsControlPointChar = null
        _isTrainerConnected.value = false
        _trainerControlAvailable.value = false

        // Reset cadence tracking state
        isFirstCscCrankMeasurement = true
        previousCscCrankRevolutions = 0
        previousCscCrankEventTime = 0
        lastKnownCadence = 0
        cadenceStaleCounter = 0

        // Reset CPS crank state
        isFirstCrankMeasurement = true
        previousCrankRevolutions = 0
        previousCrankEventTime = 0

        powerSmoother.clear()

        Timber.d("Disconnected smart trainer")
    }

    /**
     * Request control of the FTMS trainer
     */
    fun requestFtmsControl() {
        writeFtmsControlPoint(byteArrayOf(BleConstants.FTMS_REQUEST_CONTROL))
    }

    /**
     * Start or resume the FTMS workout
     */
    fun startFtmsWorkout() {
        writeFtmsControlPoint(byteArrayOf(BleConstants.FTMS_START_RESUME))
    }

    /**
     * Stop the FTMS workout
     */
    fun stopFtmsWorkout() {
        writeFtmsControlPoint(byteArrayOf(BleConstants.FTMS_STOP_PAUSE, 0x01)) // 0x01 = Stop
    }

    /**
     * Set target power on the trainer (ERG mode)
     */
    fun setTargetPower(watts: Int) {
        val data = byteArrayOf(
            BleConstants.FTMS_SET_TARGET_POWER,
            (watts and 0xFF).toByte(),
            ((watts shr 8) and 0xFF).toByte()
        )
        writeFtmsControlPoint(data)
        Timber.d("Set target power: $watts W")
    }

    /**
     * Set target power for demo mode (mock data follows this target)
     */
    fun setDemoTargetPower(watts: Int) {
        if (_isDemoMode.value) {
            mockDataGenerator.setTargetPower(watts)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeFtmsControlPoint(data: ByteArray) {
        val char = ftmsControlPointChar
        val gatt = trainerGatt
        if (char == null || gatt == null) {
            Timber.w("FTMS control point not available")
            return
        }
        char.value = data
        val success = gatt.writeCharacteristic(char)
        Timber.d("FTMS write (opcode 0x${data[0].toString(16)}): success=$success")
    }

    /**
     * Set power smoothing window in seconds
     */
    fun setPowerSmoothingWindow(seconds: Int) {
        powerSmoother.setSmoothingWindow(seconds)
        Timber.d( "Power smoothing window set to $seconds seconds")
    }
    
    /**
     * Get current power smoothing window in seconds
     */
    fun getPowerSmoothingWindow(): Int {
        return powerSmoother.getSmoothingWindow()
    }
    
    /**
     * Enable demo mode with simulated data
     */
    fun enableDemoMode() {
        if (_isDemoMode.value) return
        
        Timber.d( "Enabling demo mode")
        _isDemoMode.value = true
        
        // Mark as connected
        _isHeartRateConnected.value = true
        _isPowerMeterConnected.value = true
        _isTrainerConnected.value = true
        _trainerControlAvailable.value = true

        // Start mock data generation
        mockDataGenerator.start()
        
        // Collect and forward mock data to state flows
        mockDataScope.launch {
            mockDataGenerator.heartRateData.collect { mockHr ->
                if (_isDemoMode.value) {
                    _heartRateData.value = mockHr
                }
            }
        }
        
        mockDataScope.launch {
            mockDataGenerator.powerData.collect { mockPower ->
                if (_isDemoMode.value) {
                    // Apply power smoothing to demo data too
                    val smoothedPower = powerSmoother.addReading(mockPower.power)
                    _powerData.value = PowerData(smoothedPower, mockPower.cadence)
                }
            }
        }
    }
    
    /**
     * Disable demo mode
     */
    fun disableDemoMode() {
        if (!_isDemoMode.value) return
        
        Timber.d( "Disabling demo mode")
        _isDemoMode.value = false
        
        // Stop mock data generation
        mockDataGenerator.stop()
        
        // Reset connection states
        _isHeartRateConnected.value = false
        _isPowerMeterConnected.value = false
        _isTrainerConnected.value = false
        _trainerControlAvailable.value = false

        // Clear mock target power
        mockDataGenerator.clearTargetPower()

        // Clear data
        _heartRateData.value = HeartRateData(0)
        _powerData.value = PowerData(0, 0)
        
        // Clear power smoother
        powerSmoother.clear()
    }
    
    /**
     * Get demo mode workout phase description
     */
    fun getDemoPhaseDescription(): String {
        return if (_isDemoMode.value) {
            mockDataGenerator.getCurrentPhaseDescription()
        } else {
            ""
        }
    }
    
    /**
     * Get demo mode elapsed time
     */
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
    
    /**
     * GATT callback for heart rate monitor
     */
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
                Timber.d( "Heart rate: $heartRate bpm")
            }
        }
    }
    
    /**
     * GATT callback for power meter
     */
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
                Timber.d( "Power: $power W, Cadence: $cadence rpm")
            }
        }
    }
    
    /**
     * GATT callback for smart trainer (FTMS)
     */
    private val trainerGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("Smart trainer connected")
                    _isTrainerConnected.value = true
                    // Smart trainers like the Tacx Neo 2T are also the power/cadence
                    // source — flag this so the UI shows power as connected.
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

            // Log every service so we can debug what the trainer actually exposes.
            val allServices = gatt.services.map { it.uuid.toString() }
            Timber.i("Trainer services discovered (${allServices.size}): $allServices")

            val ftmsService = gatt.getService(BleConstants.FITNESS_MACHINE_SERVICE_UUID)
            val cpsService = gatt.getService(BleConstants.CYCLING_POWER_SERVICE_UUID)

            if (ftmsService != null) {
                // ── Primary path: FTMS ──────────────────────────────────────
                Timber.i("FTMS service found — using Indoor Bike Data + Control Point")

                // Subscribe to Indoor Bike Data notifications
                ftmsService.getCharacteristic(BleConstants.INDOOR_BIKE_DATA_CHAR_UUID)?.let { char ->
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Timber.d("Enabled Indoor Bike Data notifications")
                }

                // Cache the FTMS Control Point characteristic
                ftmsService.getCharacteristic(BleConstants.FTMS_CONTROL_POINT_CHAR_UUID)?.let { char ->
                    ftmsControlPointChar = char
                    _trainerControlAvailable.value = true
                    gatt.setCharacteristicNotification(char, true)
                    Timber.d("FTMS Control Point cached, indications pending")
                }
            } else if (cpsService != null) {
                // ── Fallback: Cycling Power Service ─────────────────────────
                // Some trainers (or trainers classified by name) don't expose
                // FTMS. We still get power + cadence from CPS, just no ERG
                // control.
                Timber.w("FTMS not found — falling back to Cycling Power Service (no ERG control)")

                cpsService.getCharacteristic(BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID)?.let { char ->
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Timber.d("Enabled Cycling Power notifications (fallback)")
                }
                // CSC subscription will be chained via onDescriptorWrite
            } else {
                Timber.e("Trainer has neither FTMS nor Cycling Power Service!")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Timber.d("Trainer descriptor write: char=${descriptor.characteristic.uuid}, status=$status")

            // After Indoor Bike Data descriptor is written, write Control Point descriptor
            if (descriptor.characteristic.uuid == BleConstants.INDOOR_BIKE_DATA_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                ftmsControlPointChar?.let { cpChar ->
                    val cpDescriptor = cpChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    cpDescriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    val written = gatt.writeDescriptor(cpDescriptor)
                    Timber.d("Writing FTMS Control Point indication descriptor: $written")
                }
            }

            // After CPS descriptor is written, chain CSC subscription for reliable cadence.
            // BLE only allows one descriptor write at a time — we must wait for the
            // CPS write to complete before subscribing to CSC.
            if (descriptor.characteristic.uuid == BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                subscribeToCSC(gatt)
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
                    // Fallback path: trainer without FTMS, reading from CPS
                    val (power, cadence) = parsePowerMeasurement(characteristic)
                    val smoothedPower = powerSmoother.addReading(power)
                    // Use CSC cadence if CPS didn't include crank data (cadence == 0)
                    val effectiveCadence = if (cadence > 0) cadence else lastKnownCadence
                    _powerData.value = PowerData(smoothedPower, effectiveCadence)
                    Timber.d("Trainer CPS fallback - Power: $power W (smoothed: $smoothedPower), Cadence: $effectiveCadence rpm (raw CPS: $cadence)")
                }
                BleConstants.CSC_MEASUREMENT_CHAR_UUID -> {
                    val cadence = parseCscMeasurement(characteristic)
                    if (cadence >= 0) {
                        lastKnownCadence = cadence
                        // Update the power data with the fresh cadence from CSC
                        val currentPower = _powerData.value.power
                        _powerData.value = PowerData(currentPower, cadence)
                        Timber.d("Trainer CSC cadence: $cadence rpm")
                    }
                }
            }
        }
    }

    /**
     * Parse Indoor Bike Data characteristic (0x2AD2)
     * Updates power and cadence flows from trainer-reported data
     */
    private fun parseIndoorBikeData(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value ?: return
        if (value.size < 2) return

        // Flags field is 2 bytes (little-endian)
        val flags = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
        var offset = 2

        // Bit 0: More Data (0 = not present, field order differs from typical)
        // Indoor Bike Data fields are present when their flag bit is 0 (inverted for some)

        // Instantaneous Speed (bit 0 = 0 means present) - uint16, 0.01 km/h
        if (flags and 0x01 == 0 && value.size >= offset + 2) {
            offset += 2 // Skip speed
        }

        // Average Speed (bit 1) - uint16
        if (flags and 0x02 != 0 && value.size >= offset + 2) {
            offset += 2
        }

        // Instantaneous Cadence (bit 2) - uint16, 0.5 rpm
        var cadence = 0
        if (flags and 0x04 != 0 && value.size >= offset + 2) {
            val rawCadence = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            cadence = rawCadence / 2 // Resolution is 0.5 rpm
            offset += 2
        }

        // Average Cadence (bit 3)
        if (flags and 0x08 != 0 && value.size >= offset + 2) {
            offset += 2
        }

        // Total Distance (bit 4) - uint24
        if (flags and 0x10 != 0 && value.size >= offset + 3) {
            offset += 3
        }

        // Resistance Level (bit 5) - sint16
        if (flags and 0x20 != 0 && value.size >= offset + 2) {
            offset += 2
        }

        // Instantaneous Power (bit 6) - sint16
        var power = 0
        if (flags and 0x40 != 0 && value.size >= offset + 2) {
            power = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
            // Handle sign for sint16
            if (power > 32767) power -= 65536
            power = power.coerceAtLeast(0)
            offset += 2
        }

        // Average Power (bit 7)
        if (flags and 0x80 != 0 && value.size >= offset + 2) {
            offset += 2
        }

        // Apply power smoothing and update flows
        if (power > 0 || cadence > 0) {
            val smoothedPower = powerSmoother.addReading(power)
            _powerData.value = PowerData(smoothedPower, cadence)
            Timber.d("Indoor Bike Data - Power: $power W (smoothed: $smoothedPower), Cadence: $cadence RPM")
        }

        // Heart Rate (bit 9) - uint8
        if (flags and 0x0200 != 0 && value.size > offset) {
            // Skip Expended Energy first (bit 8)
            if (flags and 0x0100 != 0 && value.size >= offset + 5) {
                offset += 5 // total energy (uint16) + energy/hr (uint16) + energy/min (uint8)
            }
            if (value.size > offset) {
                val heartRate = value[offset].toInt() and 0xFF
                if (heartRate > 0) {
                    _heartRateData.value = HeartRateData(heartRate)
                }
            }
        }
    }

    /**
     * Parse heart rate from characteristic data
     * Format: https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/
     */
    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int {
        val value = characteristic.value ?: return 0
        
        // First byte contains flags
        val flag = value[0].toInt()
        val format = flag and 0x01
        
        return if (format == 0) {
            // Heart rate value is in 8-bit format
            value[1].toInt() and 0xFF
        } else {
            // Heart rate value is in 16-bit format
            ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
        }
    }
    
    // Store previous crank data for cadence calculation (CPS)
    private var previousCrankRevolutions: Int = 0
    private var previousCrankEventTime: Int = 0
    private var isFirstCrankMeasurement = true

    // Store previous crank data for CSC cadence calculation
    private var previousCscCrankRevolutions: Int = 0
    private var previousCscCrankEventTime: Int = 0
    private var isFirstCscCrankMeasurement = true

    // Last known non-zero cadence — used when CPS measurements omit crank data
    private var lastKnownCadence: Int = 0
    private var cadenceStaleCounter: Int = 0
    
    /**
     * Parse power and cadence from characteristic data
     * Format: https://www.bluetooth.com/specifications/specs/cycling-power-service-1-1/
     */
    private fun parsePowerMeasurement(characteristic: BluetoothGattCharacteristic): Pair<Int, Int> {
        val value = characteristic.value ?: return Pair(0, 0)
        
        if (value.size < 4) {
            Timber.w( "Power measurement data too short: ${value.size} bytes")
            return Pair(0, 0)
        }
        
        // First two bytes contain flags (little-endian)
        val flags = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
        
        // Next two bytes contain instantaneous power (signed 16-bit, little-endian)
        val power = (value[2].toInt() and 0xFF) or ((value[3].toInt() and 0xFF) shl 8)
        
        Timber.d( "Power flags: 0x${flags.toString(16)}, Power: $power W, Data size: ${value.size} bytes")
        
        var cadence = 0
        var offset = 4 // Start after power field
        
        // Parse optional fields based on flags
        // Bit 0: Pedal Power Balance Present
        if (flags and 0x01 != 0 && value.size > offset) {
            offset += 1 // Skip pedal power balance
        }
        
        // Bit 1: Pedal Power Balance Reference (just a flag, no data)
        
        // Bit 2: Accumulated Torque Present
        if (flags and 0x04 != 0 && value.size >= offset + 2) {
            offset += 2 // Skip accumulated torque
        }
        
        // Bit 3: Accumulated Torque Source (just a flag, no data)
        
        // Bit 4: Wheel Revolution Data Present
        if (flags and 0x10 != 0 && value.size >= offset + 6) {
            offset += 6 // Skip wheel revolution data (4 bytes revolutions + 2 bytes time)
        }
        
        // Bit 5: Crank Revolution Data Present - THIS IS WHERE CADENCE COMES FROM
        if (flags and 0x20 != 0 && value.size >= offset + 4) {
            // Cumulative Crank Revolutions (uint16)
            val crankRevolutions = (value[offset].toInt() and 0xFF) or 
                                   ((value[offset + 1].toInt() and 0xFF) shl 8)
            
            // Last Crank Event Time (uint16) - in 1/1024 seconds
            val crankEventTime = (value[offset + 2].toInt() and 0xFF) or 
                                ((value[offset + 3].toInt() and 0xFF) shl 8)
            
            // Calculate cadence from delta
            if (!isFirstCrankMeasurement) {
                var revolutionsDelta = crankRevolutions - previousCrankRevolutions
                var timeDelta = crankEventTime - previousCrankEventTime
                
                // Handle rollover (uint16 wraps at 65536)
                if (revolutionsDelta < 0) revolutionsDelta += 65536
                if (timeDelta < 0) timeDelta += 65536
                
                if (timeDelta > 0) {
                    // Cadence = (revolutions / time) * 60 * 1024
                    // time is in 1/1024 seconds, so we need to convert to RPM
                    cadence = ((revolutionsDelta * 60 * 1024) / timeDelta).toInt()
                    
                    // Sanity check (cadence should be 0-250 RPM for cycling)
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
            Timber.d( "Crank data - Revolutions: $crankRevolutions, Time: $crankEventTime, Cadence: $cadence RPM")
        } else {
            // CPS measurement without crank data — use last known cadence but
            // decay it to zero after ~5 seconds of missing data (prevent stale display)
            cadenceStaleCounter++
            if (cadenceStaleCounter > 5) {
                lastKnownCadence = 0
            }
            cadence = lastKnownCadence
            Timber.d( "No crank data in CPS (flags: 0x${flags.toString(16)}), using lastKnownCadence=$cadence")
        }
        
        // Apply power smoothing
        val smoothedPower = powerSmoother.addReading(power)
        Timber.d( "Raw power: $power W, Smoothed power: $smoothedPower W (${powerSmoother.getSmoothingWindow()}s average)")
        
        return Pair(smoothedPower, cadence)
    }
    
    /**
     * Subscribe to the Cycling Speed and Cadence (CSC) service if the trainer
     * exposes it. Called after CPS descriptor write completes so we don't
     * clash with concurrent BLE descriptor writes.
     */
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

    /**
     * Parse Cycling Speed and Cadence (CSC) Measurement characteristic (0x2A5B).
     * Returns calculated cadence in RPM, or -1 if not yet available.
     *
     * CSC Measurement format:
     *   byte 0: flags
     *     - bit 0: Wheel Revolution Data Present
     *     - bit 1: Crank Revolution Data Present
     *   followed by optional wheel data (4 + 2 bytes) and/or crank data (2 + 2 bytes)
     */
    private fun parseCscMeasurement(characteristic: BluetoothGattCharacteristic): Int {
        val value = characteristic.value ?: return -1
        if (value.isEmpty()) return -1

        val flags = value[0].toInt() and 0xFF
        var offset = 1

        // Bit 0: Wheel Revolution Data Present
        if (flags and 0x01 != 0) {
            offset += 6 // Skip cumulative wheel revolutions (uint32) + last wheel event time (uint16)
        }

        // Bit 1: Crank Revolution Data Present
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

            // Handle uint16 rollover
            if (revDelta < 0) revDelta += 65536
            if (timeDelta < 0) timeDelta += 65536

            previousCscCrankRevolutions = crankRevolutions
            previousCscCrankEventTime = crankEventTime

            if (timeDelta > 0) {
                // CSC crank event time is in 1/1024 seconds
                val cadence = ((revDelta * 60 * 1024) / timeDelta)
                return if (cadence in 0..250) cadence else 0
            }

            // timeDelta == 0 means no crank movement — cadence is 0
            return 0
        }

        return -1
    }

    /**
     * Clean up resources
     */
    @SuppressLint("MissingPermission")
    fun cleanup() {
        stopScan()
        disconnectHeartRateMonitor()
        disconnectPowerMeter()
        disconnectTrainer()
    }
}

/**
 * Lowercase substrings found in the BLE names of popular smart trainers.
 * Used during scanning to classify devices that advertise Cycling Power
 * but not FTMS (FTMS is only discoverable after connecting on many trainers).
 */
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
