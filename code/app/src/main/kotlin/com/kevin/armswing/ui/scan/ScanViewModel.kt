package com.kevin.armswing.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.armswing.ble.BleManager
import com.kevin.armswing.data.repository.SessionRepository
import com.kevin.armswing.data.repository.SettingsRepository
import com.kevin.shared.ble.ConnectionState
import com.kevin.shared.domain.DiscoveredDevice
import com.kevin.shared.domain.SavedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleManager.scanResults
        .map { results ->
            listOf(DiscoveredDevice.Fake(BleManager.FAKE_DEVICE_NAME)) + results.map { DiscoveredDevice.Real(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(DiscoveredDevice.Fake(BleManager.FAKE_DEVICE_NAME)))

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val activeSessionId: StateFlow<Long?> = sessionRepository.activeSessionId

    val savedDevices: StateFlow<List<SavedDevice>> = settingsRepository.savedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val autoConnect: StateFlow<Boolean> = settingsRepository.autoConnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var pendingDeviceInfo: Pair<String, String>? = null

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Ready) {
                    pendingDeviceInfo?.let { (address, name) ->
                        settingsRepository.addSavedDevice(SavedDevice(address, name))
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        _isScanning.value = true
        bleManager.startScan()
        viewModelScope.launch {
            if (settingsRepository.autoConnect.first() &&
                connectionState.value is ConnectionState.Disconnected
            ) {
                val saved = settingsRepository.savedDevices.first()
                if (saved.isNotEmpty()) bleManager.connectToAddress(saved.first().address)
            }
        }
    }

    fun stopScan() {
        _isScanning.value = false
        bleManager.stopScan()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        pendingDeviceInfo = device.address to (device.name ?: "Unbekanntes GerÃ¤t")
        bleManager.stopScan()
        bleManager.connect(device)
    }

    fun connectToDiscovered(device: DiscoveredDevice) {
        stopScan()
        when (device) {
            is DiscoveredDevice.Fake -> {
                pendingDeviceInfo = device.address to device.displayName
                bleManager.connectFake()
            }
            is DiscoveredDevice.Real -> connect(device.scanResult.device)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToSaved(device: SavedDevice) {
        stopScan()
        bleManager.connectToAddress(device.address)
    }

    fun disconnect() = bleManager.disconnect()

    fun forgetDevice(device: SavedDevice) = viewModelScope.launch {
        settingsRepository.removeSavedDevice(device.address)
    }

    fun toggleAutoConnect() = viewModelScope.launch {
        settingsRepository.setAutoConnect(!autoConnect.value)
    }

    fun startSession(label: String = "Training") = viewModelScope.launch {
        sessionRepository.startSession(label)
    }

    fun stopSession() = viewModelScope.launch { sessionRepository.stopSession() }

    override fun onCleared() {
        super.onCleared()
        _isScanning.value = false
        bleManager.stopScan()
    }
}
