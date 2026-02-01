package com.trainstudent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.trainstudent.model.ParsedStation
import com.trainstudent.model.ParsingField
import com.trainstudent.model.PartialTranscript
import com.trainstudent.model.TravelIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRecognizerHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stationMatcher: StationMatcher,
    private val timeParser: TimeParser
) : VoiceInputHandler {

    companion object {
        private val FROM_TO_PATTERN = Regex(
            """from\s+(.+?)\s+to\s+(.+?)(?:\s+(?:at|tomorrow|today|on|next|\d).*)?$""",
            RegexOption.IGNORE_CASE
        )
        private val SIMPLE_PATTERN = Regex(
            """^(.+?)\s+to\s+(.+?)(?:\s+(?:at|tomorrow|today|on|next|\d).*)?$""",
            RegexOption.IGNORE_CASE
        )
    }

    override val providerId: String = "SPEECH_RECOGNIZER"
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    override fun startListening(): Flow<PartialTranscript> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            close(IllegalStateException("Speech recognition not available"))
            return@callbackFlow
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        isCurrentlyListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.UK.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(PartialTranscript("", TravelIntent(), true, ParsingField.ORIGIN))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                val intent = parseTranscriptSync(text)
                val field = determineParsingField(intent)
                trySend(PartialTranscript(text, intent, true, field))
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                val intent = parseTranscriptSync(text)
                trySend(PartialTranscript(text, intent, false, null))
                close()
            }

            override fun onError(error: Int) {
                isCurrentlyListening = false
                close(Exception(errorMessage(error)))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isCurrentlyListening = false }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)

        awaitClose {
            isCurrentlyListening = false
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    override fun stopListening() {
        isCurrentlyListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun isListening(): Boolean = isCurrentlyListening

    override suspend fun parseTranscript(transcript: String): TravelIntent = parseTranscriptSync(transcript)

    private fun parseTranscriptSync(transcript: String): TravelIntent {
        val lower = transcript.lowercase()
        var origin: ParsedStation? = null
        var destination: ParsedStation? = null

        // Try "from X to Y" pattern first
        FROM_TO_PATTERN.find(lower)?.let { match ->
            origin = stationMatcher.matchStation(match.groupValues[1].trim())
            destination = stationMatcher.matchStation(match.groupValues[2].trim())
        }

        // Fallback to "X to Y" pattern
        if (origin == null && destination == null) {
            SIMPLE_PATTERN.find(lower)?.let { match ->
                origin = stationMatcher.matchStation(match.groupValues[1].trim())
                destination = stationMatcher.matchStation(match.groupValues[2].trim())
            }
        }

        val time = timeParser.parseTime(lower)

        return TravelIntent(
            origin = origin,
            destination = destination,
            departureTime = time,
            isComplete = origin != null && destination != null && time != null
        )
    }

    private fun determineParsingField(intent: TravelIntent): ParsingField? = when {
        intent.origin == null -> ParsingField.ORIGIN
        intent.destination == null -> ParsingField.DESTINATION
        intent.departureTime == null -> ParsingField.TIME
        else -> null
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error: $error"
    }

    override suspend fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun getModelStatus(): ModelStatus = ModelStatus.Ready
}
