package com.trainstudent.ui.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainstudent.data.ProviderMetricsSummary
import com.trainstudent.data.VoiceMetricsDao
import com.trainstudent.model.BookingPlan
import com.trainstudent.model.ParsedDateTime
import com.trainstudent.model.ParsedStation
import com.trainstudent.model.ParsingField
import com.trainstudent.model.PartialTranscript
import com.trainstudent.model.TravelIntent
import com.trainstudent.repository.TrainStudentRepository
import com.trainstudent.voice.CactusLlmHandler
import com.trainstudent.voice.CactusModelSize
import com.trainstudent.voice.CorrectedField
import com.trainstudent.voice.SpeechRecognizerHandler
import com.trainstudent.voice.VoiceInputHandler
import com.trainstudent.voice.VoiceMetricsCollector
import com.trainstudent.voice.VoiceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaveMoneyUiState(
    val origin: ParsedStation? = null,
    val destination: ParsedStation? = null,
    val departureTime: ParsedDateTime? = null,
    val isListening: Boolean = false,
    val parsingField: ParsingField? = null,
    val rawTranscript: String = "",
    val bookingPlans: List<BookingPlan> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedProvider: VoiceProvider = VoiceProvider.SPEECH_RECOGNIZER,
    val showSettingsSheet: Boolean = false,
    val showTimePickerSheet: Boolean = false,
    val showStationPicker: StationPickerTarget? = null,
    val latencyToast: String? = null
)

enum class StationPickerTarget {
    ORIGIN,
    DESTINATION
}

@HiltViewModel
class SaveMoneyViewModel @Inject constructor(
    private val speechRecognizerHandler: SpeechRecognizerHandler,
    private val cactusLlmHandler: CactusLlmHandler,
    private val repository: TrainStudentRepository,
    private val metricsCollector: VoiceMetricsCollector,
    private val voiceMetricsDao: VoiceMetricsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaveMoneyUiState())
    val uiState: StateFlow<SaveMoneyUiState> = _uiState.asStateFlow()

    private val _metricsSummary = MutableStateFlow<List<ProviderMetricsSummary>>(emptyList())
    val metricsSummary: StateFlow<List<ProviderMetricsSummary>> = _metricsSummary.asStateFlow()

    private var voiceJob: Job? = null
    private var startTime: Long = 0

    init {
        // Load metrics summary
        viewModelScope.launch {
            voiceMetricsDao.getProviderSummaries().collect { summaries ->
                _metricsSummary.value = summaries
            }
        }
    }

    private fun getCurrentHandler(): VoiceInputHandler {
        return when (_uiState.value.selectedProvider) {
            VoiceProvider.SPEECH_RECOGNIZER -> speechRecognizerHandler
            VoiceProvider.CACTUS_7B -> {
                cactusLlmHandler.setModelSize(CactusModelSize.SEVEN_B)
                cactusLlmHandler
            }
            VoiceProvider.CACTUS_13B -> {
                cactusLlmHandler.setModelSize(CactusModelSize.THIRTEEN_B)
                cactusLlmHandler
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun startVoiceInput() {
        if (_uiState.value.isListening) {
            stopVoiceInput()
            return
        }

        val handler = getCurrentHandler()
        startTime = System.currentTimeMillis()
        metricsCollector.startSession(handler.providerId)

        voiceJob = viewModelScope.launch {
            handler.startListening()
                .debounce(300) // 300ms debounce for chip updates
                .catch { e ->
                    _uiState.update { it.copy(
                        isListening = false,
                        searchError = e.message
                    )}
                    metricsCollector.cancelSession()
                }
                .collect { partial ->
                    handlePartialTranscript(partial)
                }
        }
    }

    private fun handlePartialTranscript(partial: PartialTranscript) {
        metricsCollector.updateTranscript(partial.rawText)

        _uiState.update { current ->
            current.copy(
                origin = partial.intent.origin ?: current.origin,
                destination = partial.intent.destination ?: current.destination,
                departureTime = partial.intent.departureTime ?: current.departureTime,
                isListening = partial.isListening,
                parsingField = partial.parsingField,
                rawTranscript = partial.rawText
            )
        }

        // If listening stopped and intent is complete, trigger search
        if (!partial.isListening && partial.intent.isComplete) {
            val latencyMs = System.currentTimeMillis() - startTime
            metricsCollector.endSession(partial.intent)
            
            _uiState.update { it.copy(
                latencyToast = "${latencyMs}ms"
            )}
            
            searchBookingPlans()
        }
    }

    fun stopVoiceInput() {
        voiceJob?.cancel()
        voiceJob = null
        getCurrentHandler().stopListening()
        
        _uiState.update { it.copy(isListening = false) }
        
        // End metrics session with current intent
        val currentIntent = TravelIntent(
            origin = _uiState.value.origin,
            destination = _uiState.value.destination,
            departureTime = _uiState.value.departureTime,
            isComplete = _uiState.value.origin != null && 
                         _uiState.value.destination != null && 
                         _uiState.value.departureTime != null
        )
        metricsCollector.endSession(currentIntent)
    }

    fun updateOrigin(station: ParsedStation) {
        val wasNull = _uiState.value.origin == null
        val changed = _uiState.value.origin?.crs != station.crs
        
        if (!wasNull && changed) {
            metricsCollector.recordCorrection(CorrectedField.ORIGIN)
        }
        
        _uiState.update { it.copy(origin = station, showStationPicker = null) }
        checkAndSearch()
    }

    fun updateDestination(station: ParsedStation) {
        val wasNull = _uiState.value.destination == null
        val changed = _uiState.value.destination?.crs != station.crs
        
        if (!wasNull && changed) {
            metricsCollector.recordCorrection(CorrectedField.DESTINATION)
        }
        
        _uiState.update { it.copy(destination = station, showStationPicker = null) }
        checkAndSearch()
    }

    fun updateDepartureTime(time: ParsedDateTime) {
        val wasNull = _uiState.value.departureTime == null
        val changed = _uiState.value.departureTime?.isoString != time.isoString
        
        if (!wasNull && changed) {
            metricsCollector.recordCorrection(CorrectedField.TIME)
        }
        
        _uiState.update { it.copy(departureTime = time, showTimePickerSheet = false) }
        checkAndSearch()
    }

    private fun checkAndSearch() {
        val state = _uiState.value
        if (state.origin != null && state.destination != null && state.departureTime != null) {
            searchBookingPlans()
        }
    }

    fun searchBookingPlans() {
        val state = _uiState.value
        val origin = state.origin ?: return
        val destination = state.destination ?: return
        val time = state.departureTime ?: return

        _uiState.update { it.copy(isSearching = true, searchError = null) }

        viewModelScope.launch {
            repository.searchDiscounts(
                from = origin,
                to = destination,
                datetime = time
            ).onSuccess { plans ->
                _uiState.update { it.copy(
                    bookingPlans = plans,
                    isSearching = false
                )}
            }.onFailure { e ->
                _uiState.update { it.copy(
                    searchError = e.message,
                    isSearching = false
                )}
            }
        }
    }

    fun setVoiceProvider(provider: VoiceProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun showTimePicker(show: Boolean) {
        _uiState.update { it.copy(showTimePickerSheet = show) }
    }

    fun showStationPicker(target: StationPickerTarget?) {
        _uiState.update { it.copy(showStationPicker = target) }
    }

    fun clearLatencyToast() {
        _uiState.update { it.copy(latencyToast = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(searchError = null) }
    }
    
    fun cancelSearch() {
        // Cancel any ongoing search
        _uiState.update { it.copy(isSearching = false) }
    }
    
    fun updateDate(date: java.time.LocalDate) {
        // Update the departure date
        val currentTime = _uiState.value.departureTime
        val newDateTime = ParsedDateTime(
            displayText = date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")) + 
                (currentTime?.displayText?.let { " at ${it.substringAfter("at ").trim()}" } ?: ""),
            isoString = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toString(),
            confidence = 1.0f
        )
        _uiState.update { it.copy(departureTime = newDateTime) }
        checkAndSearch()
    }
}
