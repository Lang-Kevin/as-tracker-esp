package com.kevin.armswing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.armswing.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val savedDeviceAddress: StateFlow<String?> = settingsRepository.savedDeviceAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autoConnect: StateFlow<Boolean> = settingsRepository.autoConnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun clearSavedDevice() = viewModelScope.launch { settingsRepository.clearSavedDevice() }

    fun toggleAutoConnect() = viewModelScope.launch {
        settingsRepository.setAutoConnect(!autoConnect.value)
    }
}
