package com.trainstudent.voice

import com.trainstudent.model.PartialTranscript
import com.trainstudent.model.TravelIntent
import kotlinx.coroutines.flow.Flow

/**
 * Voice input provider abstraction.
 * Supports multiple backends: Android SpeechRecognizer, Cactus LLM 7B, Cactus LLM 13B
 */
interface VoiceInputHandler {
    
    /**
     * Provider identifier for metrics tracking
     */
    val providerId: String
    
    /**
     * Start listening for voice input.
     * Returns a flow of partial transcripts as speech is recognized.
     * Flow completes when user stops speaking or timeout occurs.
     */
    fun startListening(): Flow<PartialTranscript>
    
    /**
     * Stop listening immediately
     */
    fun stopListening()
    
    /**
     * Check if this provider is currently listening
     */
    fun isListening(): Boolean
    
    /**
     * Parse a raw transcript into a TravelIntent.
     * Used for re-parsing after transcript updates.
     */
    suspend fun parseTranscript(transcript: String): TravelIntent
    
    /**
     * Check if this provider is available and ready
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Get the current model status (for Cactus providers)
     */
    fun getModelStatus(): ModelStatus
}

enum class VoiceProvider(val displayName: String, val modelSize: String?) {
    SPEECH_RECOGNIZER("Android Speech", null),
    CACTUS_7B("Qwen3 Small (Cactus)", "~400MB"),
    CACTUS_13B("Qwen3 Large (Cactus)", "~1.1GB")
}
