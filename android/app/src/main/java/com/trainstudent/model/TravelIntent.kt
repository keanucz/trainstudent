package com.trainstudent.model

import kotlinx.serialization.Serializable

@Serializable
data class TravelIntent(
    val origin: ParsedStation? = null,
    val destination: ParsedStation? = null,
    val departureTime: ParsedDateTime? = null,
    val isComplete: Boolean = false
)

@Serializable
data class ParsedStation(
    val name: String,
    val crs: String,
    val confidence: Float,
    val alternatives: List<StationCandidate> = emptyList()
)

@Serializable
data class StationCandidate(
    val name: String,
    val crs: String,
    val score: Float
)

@Serializable
data class ParsedDateTime(
    val isoString: String,          // "2026-02-01T17:00:00"
    val displayText: String,        // "Tomorrow 5pm"
    val confidence: Float,
    val isRelative: Boolean = false // "in 2 hours" vs "at 5pm"
)

data class PartialTranscript(
    val rawText: String,
    val intent: TravelIntent,
    val isListening: Boolean,
    val parsingField: ParsingField?
)

enum class ParsingField {
    ORIGIN,
    DESTINATION,
    TIME
}
