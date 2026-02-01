package com.trainstudent.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainstudent.model.TrainService
import com.trainstudent.repository.TrainStudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LiveTrainsUiState(
    val fromStation: String = "London Euston",
    val toStation: String = "Manchester Piccadilly",
    val services: List<TrainService> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val error: String? = null,
    val lastUpdated: String = "--:--"
)

@HiltViewModel
class LiveTrainsViewModel @Inject constructor(
    private val repository: TrainStudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTrainsUiState())
    val uiState: StateFlow<LiveTrainsUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    init {
        refresh()
    }

    fun setFromStation(station: String) {
        _uiState.update { it.copy(fromStation = station) }
    }

    fun setToStation(station: String) {
        _uiState.update { it.copy(toStation = station) }
    }

    fun swapStations() {
        _uiState.update {
            it.copy(
                fromStation = it.toStation,
                toStation = it.fromStation
            )
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val from = _uiState.value.fromStation
            val to = _uiState.value.toStation

            repository.refreshLiveTrains(from, to)
                .onSuccess { services ->
                    _uiState.update {
                        it.copy(
                            services = services,
                            isLoading = false,
                            lastUpdated = LocalTime.now().format(timeFormatter)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
        }
    }
    
    fun startVoiceInput() {
        _uiState.update { it.copy(isListening = true) }
        // TODO: Integrate with voice recognition service
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // Simulate listening
            _uiState.update { it.copy(isListening = false) }
        }
    }
}
