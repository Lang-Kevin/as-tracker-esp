package com.kevin.armswing.ui.profile

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
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val spineToShoulder: StateFlow<Float> = settingsRepository.spineToShoulder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20f)

    val shoulderToElbow: StateFlow<Float> = settingsRepository.shoulderToElbow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30f)

    val sensorRadius: StateFlow<Float> = settingsRepository.sensorRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)

    fun save(spineToShoulder: Float, shoulderToElbow: Float) = viewModelScope.launch {
        settingsRepository.setSpineToShoulder(spineToShoulder)
        settingsRepository.setShoulderToElbow(shoulderToElbow)
    }
}
