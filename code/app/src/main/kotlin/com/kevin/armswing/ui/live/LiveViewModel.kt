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

    private val _currentVelocityMs = MutableStateFlow<Float?>(null)
    val currentVelocityMs: StateFlow<Float?> = _currentVelocityMs.asStateFlow()

    val currentVelocityKmh: StateFlow<Float?> = _currentVelocityMs
        .map { it?.times(3.6f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _velocityHistory = MutableStateFlow<List<Float>>(emptyList())
    val velocityHistory: StateFlow<List<Float>> = _velocityHistory.asStateFlow()

    private val _sessionPeakMs = MutableStateFlow<Float?>(null)
    val sessionPeakMs: StateFlow<Float?> = _sessionPeakMs.asStateFlow()

    val avgVelocityMs: StateFlow<Float?> = _velocityHistory.map { history ->
        if (history.isEmpty()) null else history.average().toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    private var sessionStartMs = 0L

    init {
        viewModelScope.launch {
            bleManager.velocityReadings.collect { reading ->
                val v = reading.velocityMps
                _currentVelocityMs.value = v
                _velocityHistory.value = (_velocityHistory.value + v).takeLast(300)
                _sessionPeakMs.update { current ->
                    if (current == null || v > current) v else current
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
                    _currentVelocityMs.value = null
                    _velocityHistory.value = emptyList()
                    _sessionPeakMs.value = null
                    _sampleCount.value = 0
                }
            }
        }
    }
}
