package com.cycling.workitout.ui.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.firestore.UserProfileRepository
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PairingStep {
    TRAINER,
    CADENCE,
    HEART_RATE,
    PROFILE,
    READY
}

class FirstRunPairingViewModel(
    private val bleManager: BleManager,
    private val preferences: ThemePreferences,
    private val deviceRepository: DeviceRepository = WorkItOutApplication.deviceRepository,
    private val userProfileRepository: UserProfileRepository = WorkItOutApplication.userProfileRepository
) : ViewModel() {

    private val _step = MutableStateFlow(PairingStep.TRAINER)
    val step: StateFlow<PairingStep> = _step.asStateFlow()

    private val _ftp = MutableStateFlow(ThemePreferences.DEFAULT_FTP_WATTS)
    val ftp: StateFlow<Int> = _ftp.asStateFlow()

    private val _weightKg = MutableStateFlow(ThemePreferences.DEFAULT_WEIGHT_KG)
    val weightKg: StateFlow<Int> = _weightKg.asStateFlow()

    private val _maxHeartRate = MutableStateFlow(ThemePreferences.DEFAULT_MAX_HR)
    val maxHeartRate: StateFlow<Int> = _maxHeartRate.asStateFlow()

    private var wentThroughProfileStep = false

    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTrainerConnected: StateFlow<Boolean> = bleManager.isTrainerConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isHeartRateConnected: StateFlow<Boolean> = bleManager.isHeartRateConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCadenceSensorConnected: StateFlow<Boolean> = bleManager.isCadenceSensorConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val trainerProvidesCadence: StateFlow<Boolean> = bleManager.trainerProvidesCadence
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
                DeviceType.CADENCE_SENSOR -> bleManager.connectCadenceSensor(device)
                else -> Unit
            }
            deviceRepository.saveDevice(device)
        }
    }

    fun nextStep() {
        viewModelScope.launch {
            _step.value = when (_step.value) {
                // Skip the Cadence step when the trainer already broadcasts cadence — most modern
                // smart trainers do, so most users won't ever see this step.
                PairingStep.TRAINER -> {
                    // Read from BleManager directly — the VM's stateIn(WhileSubscribed) wrapper
                    // doesn't reflect upstream until a UI collector subscribes, which never happens
                    // for this flag.
                    if (bleManager.trainerProvidesCadence.value) PairingStep.HEART_RATE
                    else PairingStep.CADENCE
                }
                PairingStep.CADENCE -> PairingStep.HEART_RATE
                PairingStep.HEART_RATE -> {
                    if (userProfileRepository.hasExistingProfile()) PairingStep.READY
                    else { wentThroughProfileStep = true; PairingStep.PROFILE }
                }
                PairingStep.PROFILE -> PairingStep.READY
                PairingStep.READY -> PairingStep.READY
            }
        }
    }

    fun setFtp(watts: Int) {
        _ftp.value = watts.coerceIn(50, 600)
    }

    fun setWeightKg(kg: Int) {
        _weightKg.value = kg.coerceIn(30, 200)
    }

    fun setMaxHeartRate(bpm: Int) {
        _maxHeartRate.value = bpm.coerceIn(120, 230)
    }

    fun completeFirstRun(onDone: () -> Unit) {
        viewModelScope.launch {
            if (wentThroughProfileStep) {
                userProfileRepository.setFtp(_ftp.value)
                userProfileRepository.setWeightKg(_weightKg.value)
                userProfileRepository.setMaxHr(_maxHeartRate.value)
            }
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
