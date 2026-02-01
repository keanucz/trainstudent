package com.trainstudent.voice

/**
 * Represents available Cactus LLM model sizes.
 * 
 * These map to actual Cactus SDK models:
 * - SEVEN_B -> qwen3-0.6 (~400MB, fastest)
 * - THIRTEEN_B -> qwen3-1.7 (~1.1GB, more capable)
 */
enum class CactusModelSize(
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val modelSlug: String
) {
    SEVEN_B("Qwen3 Small", "qwen3-0.6", 400_000_000L, "qwen3-0.6"),
    THIRTEEN_B("Qwen3 Large", "qwen3-1.7", 1_100_000_000L, "qwen3-1.7");

    companion object {
        fun values() = CactusModelSize.entries.toTypedArray()
    }
}
