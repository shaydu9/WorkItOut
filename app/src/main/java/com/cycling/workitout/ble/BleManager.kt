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
            
            // Determine device type based on advertised services
            val deviceType = when {
                result.scanRecord?.serviceUuids?.any { 
                    it.uuid == BleConstants.HEART_RATE_SERVICE_UUID 
                } == true -> DeviceType.HEART_RATE_MONITOR
                
                result.scanRecord?.serviceUuids?.any { 
                    it.uuid == BleConstants.CYCLING_POWER_SERVICE_UUID 
                } == true -> DeviceType.POWER_METER
                
                result.scanRecord?.serviceUuids?.any { 
                    it.uuid == BleConstants.FITNESS_MACHINE_SERVICE_UUID 
                } == true -> DeviceType.SMART_TRAINER
                
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
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("Smart trainer disconnected")
                    _isTrainerConnected.value = false
                    _trainerControlAvailable.value = false
                    ftmsControlPointChar = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val ftmsService = gatt.getService(BleConstants.FITNESS_MACHINE_SERVICE_UUID)
            if (ftmsService == null) {
                Timber.e("FTMS service not found on trainer")
                return
            }

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

                // Enable indications on control point (uses INDICATION, not NOTIFICATION)
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                descriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                // Will write after bike data descriptor write completes
                Timber.d("FTMS Control Point cached, indications pending")
            }

            // Also try to get power/cadence from Cycling Power Service if available
            val cpsService = gatt.getService(BleConstants.CYCLING_POWER_SERVICE_UUID)
            cpsService?.getCharacteristic(BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID)?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                Timber.d("Trainer also exposes Cycling Power Service")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // After Indoor Bike Data descriptor is written, write Control Point descriptor
            if (descriptor.characteristic.uuid == BleConstants.INDOOR_BIKE_DATA_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                ftmsControlPointChar?.let { cpChar ->
                    val cpDescriptor = cpChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    cpDescriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    @SuppressLint("MissingPermission")
                    val written = gatt.writeDescriptor(cpDescriptor)
                    Timber.d("Writing FTMS Control Point indication descriptor: $written")
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
                    val (power, cadence) = parsePowerMeasurement(characteristic)
                    _powerData.value = PowerData(power, cadence)
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
    
    // Store previous crank data for cadence calculation
    private var previousCrankRevolutions: Int = 0
    private var previousCrankEventTime: Int = 0
    private var isFirstCrankMeasurement = true
    
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
            
            Timber.d( "Crank data - Revolutions: $crankRevolutions, Time: $crankEventTime, Cadence: $cadence RPM")
        } else {
            Timber.d( "No crank revolution data in this measurement (flags: 0x${flags.toString(16)})")
        }
        
        // Apply power smoothing
        val smoothedPower = powerSmoother.addReading(power)
        Timber.d( "Raw power: $power W, Smoothed power: $smoothedPower W (${powerSmoother.getSmoothingWindow()}s average)")
        
        return Pair(smoothedPower, cadence)
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
