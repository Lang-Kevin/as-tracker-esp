package com.kevin.armswing.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.armswing.ble.BleManager
import com.kevin.armswing.ble.ConnectionState
import com.kevin.armswing.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val bleManager: BleManager,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val activeSessionId: StateFlow<Long?> = sessionRepository.activeSessionId

    val sessionLabel: StateFlow<String?> = sessionRepository.activeSession
        .map { it?.label }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentOmega = MutableStateFlow<Float?>(null)
    val currentOmega: StateFlow<Float?> = _currentOmega.asStateFlow()

    private val _omegaHistory = MutableStateFlow<List<Float>>(emptyList())
    val omegaHistory: StateFlow<List<Float>> = _omegaHistory.asStateFlow()

    private val _maxOmega = MutableStateFlow<Float?>(null)
    val maxOmega: StateFlow<Float?> = _maxOmega.asStateFlow()

    val avgOmega: StateFlow<Float?> = _omegaHistory.map { history ->
        if (history.isEmpty()) null else history.average().toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    private var sessionStartMs = 0L

    init {
        viewModelScope.launch {
            bleManager.omegaReadings.collect { reading ->
                _currentOmega.value = reading.omega
                _omegaHistory.value = (_omegaHistory.value + reading.omega).takeLast(300)
                _maxOmega.update { current ->
                    if (current == null || reading.omega > current) reading.omega else current
                }
                _sampleCount.update { it + 1 }
            }
        }
        viewModelScope.launch {
            while (true) {
                if (activeSessionId.value != null && sessionStartMs > 0) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - sessionStartMs) / 1000
                }
                delay(1_000)
            }
        }
        viewModelScope.launch {
            activeSessionId.collect { id ->
                if (id != null && sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
                if (id == null) {
                    sessionStartMs = 0L
                    _elapsedSeconds.value = 0
                    _currentOmega.value = null
                    _omegaHistory.value = emptyList()
                    _maxOmega.value = null
                    _sampleCount.value = 0
                }
            }
        }
    }
}
