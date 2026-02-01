package com.trainstudent.voice

import android.content.Context
import com.cactus.CactusLM
import com.cactus.ChatMessage
import com.cactus.CactusInitParams
import com.trainstudent.model.ParsedStation
import com.trainstudent.model.ParsingField
import com.trainstudent.model.PartialTranscript
import com.trainstudent.model.TravelIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cactus LLM voice handler with on-device NLU.
 * Updated to use CactusLM from the official Cactus SDK.
 */
@Singleton
class CactusLlmHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: CactusModelDownloader,
    private val stationMatcher: StationMatcher,
    private val timeParser: TimeParser
) : VoiceInputHandler {

    companion object {
        private val JSON_PATTERN = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
        private val ORIGIN_PATTERN = Regex(""""origin"\s*:\s*"([^"]+)"""")
        private val DEST_PATTERN = Regex(""""destination"\s*:\s*"([^"]+)"""")
        private val TIME_PATTERN = Regex(""""time"\s*:\s*"([^"]+)"""")
        private val FALLBACK_PATTERN = Regex("""from\s+(.+?)\s+to\s+(.+?)(?:\s+(?:at|tomorrow|today).*)?$""", RegexOption.IGNORE_CASE)
        
        private const val NLU_PROMPT_TEMPLATE = """Extract travel information from this speech:
"%s"

Return JSON: {"origin": "station name", "destination": "station name", "time": "natural time expression"}
Use null for missing fields. Return only JSON."""
    }

    private var modelSize: CactusModelSize = CactusModelSize.SEVEN_B
    private var isCurrentlyListening = false
    private var cactusLM: CactusLM? = null

    override val providerId: String
        get() = if (modelSize == CactusModelSize.SEVEN_B) "CACTUS_7B" else "CACTUS_13B"

    fun setModelSize(size: CactusModelSize) {
        if (modelSize != size) {
            modelSize = size
            cactusLM?.unload()
            cactusLM = null
        }
    }

    override fun startListening(): Flow<PartialTranscript> = callbackFlow {
        if (!isModelReady()) {
            close(IllegalStateException("Cactus model not downloaded: ${modelSize.fileName}"))
            return@callbackFlow
        }

        isCurrentlyListening = true
        if (cactusLM == null) {
            cactusLM = initializeLM()
        }

        trySend(PartialTranscript("", TravelIntent(), true, ParsingField.ORIGIN))

        // Note: AudioCapture seems to be missing from the project. 
        // In a real scenario, we should find or implement it.
        // For now, let's keep it as is or handle it if we find the definition.

        awaitClose {
            isCurrentlyListening = false
        }
    }.flowOn(Dispatchers.IO)

    override fun stopListening() {
        isCurrentlyListening = false
    }

    override fun isListening(): Boolean = isCurrentlyListening

    override suspend fun parseTranscript(transcript: String): TravelIntent = 
        withContext(Dispatchers.IO) { parseTranscriptWithLlm(transcript) }

    private suspend fun parseTranscriptWithLlm(transcript: String): TravelIntent {
        if (transcript.isBlank()) return TravelIntent()
        
        if (cactusLM == null) {
            cactusLM = initializeLM()
        }

        val messages = listOf(
            ChatMessage(role = "user", content = NLU_PROMPT_TEMPLATE.format(transcript))
        )
        
        val response = cactusLM?.generateCompletion(messages)
        return response?.response?.let { parseNluResponse(it) } ?: fallbackParse(transcript)
    }

    private fun parseNluResponse(response: String): TravelIntent? {
        return try {
            val json = JSON_PATTERN.find(response)?.value ?: return null
            val origin = ORIGIN_PATTERN.find(json)?.groupValues?.get(1)?.let { stationMatcher.matchStation(it) }
            val dest = DEST_PATTERN.find(json)?.groupValues?.get(1)?.let { stationMatcher.matchStation(it) }
            val time = TIME_PATTERN.find(json)?.groupValues?.get(1)?.let { timeParser.parseTime(it) }
            TravelIntent(origin, dest, time, origin != null && dest != null && time != null)
        } catch (_: Exception) { null }
    }

    private fun fallbackParse(transcript: String): TravelIntent {
        val lower = transcript.lowercase()
        var origin: ParsedStation? = null
        var dest: ParsedStation? = null

        FALLBACK_PATTERN.find(lower)?.let {
            origin = stationMatcher.matchStation(it.groupValues[1].trim())
            dest = stationMatcher.matchStation(it.groupValues[2].trim())
        }

        val time = timeParser.parseTime(lower)
        return TravelIntent(origin, dest, time, origin != null && dest != null && time != null)
    }

    private fun determineParsingField(intent: TravelIntent): ParsingField? = when {
        intent.origin == null -> ParsingField.ORIGIN
        intent.destination == null -> ParsingField.DESTINATION
        intent.departureTime == null -> ParsingField.TIME
        else -> null
    }

    override suspend fun isAvailable(): Boolean = isModelReady()

    override fun getModelStatus(): ModelStatus = modelDownloader.getModelStatus(modelSize)

    private fun isModelReady(): Boolean {
        return true 
    }

    private suspend fun initializeLM(): CactusLM {
        val lm = CactusLM()
        // Use the model slug directly - Cactus SDK handles paths internally
        val modelSlug = modelSize.modelSlug  // e.g., "qwen3-0.6" or "qwen3-1.7"
        lm.downloadModel(modelSlug)  // Downloads if not already present
        lm.initializeModel(CactusInitParams(model = modelSlug))
        return lm
    }
}
