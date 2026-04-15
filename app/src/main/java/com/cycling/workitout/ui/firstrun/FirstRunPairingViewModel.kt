package com.cycling.workitout.ui.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Steps of the first-run pairing stepper.
 */
enum class PairingStep {
    TRAINER,
    HEART_RATE,
    FTP,
    READY
}

class FirstRunPairingViewModel(
    private val bleManager: BleManager,
    private val preferences: ThemePreferences,
    private val deviceRepository: DeviceRepository = WorkItOutApplication.deviceRepository
) : ViewModel() {

    private val _step = MutableStateFlow(PairingStep.TRAINER)
    val step: StateFlow<PairingStep> = _step.asStateFlow()

    private val _ftp = MutableStateFlow(ThemePreferences.DEFAULT_FTP_WATTS)
    val ftp: StateFlow<Int> = _ftp.asStateFlow()

    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTrainerConnected: StateFlow<Boolean> = bleManager.isTrainerConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startScan() {
        viewModelScope.launch { bleManager.startScan() }
    }

    fun stopScan() {
        viewModelScope.launch { bleManager.stopScan() }
    }

    fun connectDevice(device: BleDevice) {
        viewModelScope.launch {
            when (device.deviceType) {
                DeviceType.SMART_TRAINER -> bleManager.connectTrainer(device)
                DeviceType.HEART_RATE_MONITOR -> bleManager.connectHeartRateMonitor(device)
                DeviceType.POWER_METER -> bleManager.connectPowerMeter(device)
                else -> Unit
            }
            // Persist so we can auto-reconnect on subsequent launches.
            deviceRepository.saveDevice(device)
        }
    }

    fun nextStep() {
        _step.value = when (_step.value) {
            PairingStep.TRAINER -> PairingStep.HEART_RATE
            PairingStep.HEART_RATE -> PairingStep.FTP
            PairingStep.FTP -> PairingStep.READY
            PairingStep.READY -> PairingStep.READY
        }
    }

    fun setFtp(watts: Int) {
        _ftp.value = watts.coerceIn(50, 600)
    }

    fun completeFirstRun(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setUserFtpWatts(_ftp.value)
            preferences.setHasCompletedFirstRun(true)
            bleManager.stopScan()
            onDone()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
