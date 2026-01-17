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
import android.util.Log
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.HeartRateData
import com.cycling.workitout.data.PowerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central BLE manager for scanning, connecting, and managing cycling sensors
 */
class BleManager(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var heartRateGatt: BluetoothGatt? = null
    private var powerMeterGatt: BluetoothGatt? = null
    
    // State flows for discovered devices
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()
    
    // State flows for connection status
    private val _isHeartRateConnected = MutableStateFlow(false)
    val isHeartRateConnected: StateFlow<Boolean> = _isHeartRateConnected.asStateFlow()
    
    private val _isPowerMeterConnected = MutableStateFlow(false)
    val isPowerMeterConnected: StateFlow<Boolean> = _isPowerMeterConnected.asStateFlow()
    
    // State flows for sensor data
    private val _heartRateData = MutableStateFlow(HeartRateData())
    val heartRateData: StateFlow<HeartRateData> = _heartRateData.asStateFlow()
    
    private val _powerData = MutableStateFlow(PowerData())
    val powerData: StateFlow<PowerData> = _powerData.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    companion object {
        private const val TAG = "BleManager"
    }
    
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
            Log.d(TAG, "Already scanning")
            return
        }
        
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled")
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
        Log.d(TAG, "Started BLE scan")
    }
    
    /**
     * Stop scanning for BLE devices
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Log.d(TAG, "Stopped BLE scan")
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
                Log.d(TAG, "Discovered device: $deviceName ($deviceType)")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }
    
    /**
     * Connect to a heart rate monitor
     */
    @SuppressLint("MissingPermission")
    fun connectHeartRateMonitor(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.HEART_RATE_MONITOR) {
            Log.e(TAG, "Device is not a heart rate monitor")
            return
        }
        
        heartRateGatt?.close()
        heartRateGatt = bleDevice.device.connectGatt(context, false, heartRateGattCallback)
        Log.d(TAG, "Connecting to heart rate monitor: ${bleDevice.name}")
    }
    
    /**
     * Connect to a power meter
     */
    @SuppressLint("MissingPermission")
    fun connectPowerMeter(bleDevice: BleDevice) {
        if (bleDevice.deviceType != DeviceType.POWER_METER) {
            Log.e(TAG, "Device is not a power meter")
            return
        }
        
        powerMeterGatt?.close()
        powerMeterGatt = bleDevice.device.connectGatt(context, false, powerMeterGattCallback)
        Log.d(TAG, "Connecting to power meter: ${bleDevice.name}")
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
        Log.d(TAG, "Disconnected heart rate monitor")
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
        
        Log.d(TAG, "Disconnected power meter")
    }
    
    /**
     * GATT callback for heart rate monitor
     */
    private val heartRateGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Heart rate monitor connected")
                    _isHeartRateConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Heart rate monitor disconnected")
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
                    
                    Log.d(TAG, "Enabled heart rate notifications")
                }
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.HEART_RATE_MEASUREMENT_CHAR_UUID) {
                val heartRate = parseHeartRate(characteristic)
                _heartRateData.value = HeartRateData(heartRate)
                Log.d(TAG, "Heart rate: $heartRate bpm")
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
                    Log.d(TAG, "Power meter connected")
                    _isPowerMeterConnected.value = true
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Power meter disconnected")
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
                    
                    Log.d(TAG, "Enabled power meter notifications")
                }
            }
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.CYCLING_POWER_MEASUREMENT_CHAR_UUID) {
                val (power, cadence) = parsePowerMeasurement(characteristic)
                _powerData.value = PowerData(power, cadence)
                Log.d(TAG, "Power: $power W, Cadence: $cadence rpm")
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
            Log.w(TAG, "Power measurement data too short: ${value.size} bytes")
            return Pair(0, 0)
        }
        
        // First two bytes contain flags (little-endian)
        val flags = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
        
        // Next two bytes contain instantaneous power (signed 16-bit, little-endian)
        val power = (value[2].toInt() and 0xFF) or ((value[3].toInt() and 0xFF) shl 8)
        
        Log.d(TAG, "Power flags: 0x${flags.toString(16)}, Power: $power W, Data size: ${value.size} bytes")
        
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
                        Log.w(TAG, "Cadence out of range: $cadence, resetting")
                        cadence = 0
                    }
                }
            } else {
                isFirstCrankMeasurement = false
            }
            
            previousCrankRevolutions = crankRevolutions
            previousCrankEventTime = crankEventTime
            
            Log.d(TAG, "Crank data - Revolutions: $crankRevolutions, Time: $crankEventTime, Cadence: $cadence RPM")
        } else {
            Log.d(TAG, "No crank revolution data in this measurement (flags: 0x${flags.toString(16)})")
        }
        
        return Pair(power, cadence)
    }
    
    /**
     * Clean up resources
     */
    @SuppressLint("MissingPermission")
    fun cleanup() {
        stopScan()
        disconnectHeartRateMonitor()
        disconnectPowerMeter()
    }
}
