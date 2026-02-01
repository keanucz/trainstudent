package com.trainstudent.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cactus.CactusLM
import com.cactus.CactusSTT
import com.cactus.CactusModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.modelPrefsDataStore by preferencesDataStore(name = "cactus_models")

/**
 * Manages Cactus LLM model downloads using the official Cactus SDK.
 * 
 * The Cactus SDK handles model downloads internally with these built-in models:
 * - LLM: qwen3-0.6, gemma3-270m, etc.
 * - STT: whisper-tiny, whisper-base
 */
@Singleton
class CactusModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // DataStore keys
    private object Keys {
        val SELECTED_LLM_MODEL = stringPreferencesKey("selected_llm_model")
        val SELECTED_STT_MODEL = stringPreferencesKey("selected_stt_model")
    }
    
    // Available models from Cactus SDK
    private val llmModels = listOf(
        CactusModel("qwen3-0.6", "Qwen3 0.6B", 400L, "Fastest, good for simple parsing"),
        CactusModel("gemma3-270m", "Gemma3 270M", 270L, "Smallest, basic capabilities"),
        CactusModel("qwen3-1.7", "Qwen3 1.7B", 1100L, "Balanced speed and capability"),
        CactusModel("gemma3-1.5", "Gemma3 1.5B", 1000L, "Good general purpose"),
    )
    
    private val sttModels = listOf(
        CactusModel("whisper-tiny", "Whisper Tiny", 75L, "Fast transcription"),
        CactusModel("whisper-base", "Whisper Base", 150L, "Better accuracy"),
    )
    
    // Progress tracking
    private val _downloadProgress = MutableStateFlow<Map<CactusModelSize, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<CactusModelSize, DownloadProgress>> = _downloadProgress
    
    // LLM instance for checking models
    private val cactusLM = CactusLM()
    private val cactusSTT = CactusSTT()
    
    init {
        // Check which models are already downloaded
        scope.launch {
            refreshModelStatus()
        }
    }
    
    /**
     * Refresh status of all models
     */
    private suspend fun refreshModelStatus() {
        val downloadedModels = CactusModelManager.getDownloadedModels()
        
        val progressMap = mutableMapOf<CactusModelSize, DownloadProgress>()
        
        // Check 7B equivalent (qwen3-0.6 or gemma3-270m)
        if (downloadedModels.any { it in listOf("qwen3-0.6", "gemma3-270m") }) {
            progressMap[CactusModelSize.SEVEN_B] = DownloadProgress(
                modelSize = CactusModelSize.SEVEN_B,
                bytesDownloaded = 400_000_000L,
                totalBytes = 400_000_000L,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED
            )
        }
        
        // Check 13B equivalent (qwen3-1.7 or gemma3-1.5)
        if (downloadedModels.any { it in listOf("qwen3-1.7", "gemma3-1.5") }) {
            progressMap[CactusModelSize.THIRTEEN_B] = DownloadProgress(
                modelSize = CactusModelSize.THIRTEEN_B,
                bytesDownloaded = 1_100_000_000L,
                totalBytes = 1_100_000_000L,
                progress = 1.0f,
                status = DownloadStatus.COMPLETED
            )
        }
        
        _downloadProgress.value = progressMap
    }
    
    /**
     * Get the models directory path for display
     */
    fun getModelsDirectoryPath(): Flow<String> {
        return context.modelPrefsDataStore.data.map { _ ->
            CactusModelManager.getModelsDirectory()
        }
    }
    
    /**
     * Check if a model is downloaded
     */
    suspend fun isModelDownloaded(modelSize: CactusModelSize): Boolean {
        val downloadedModels = CactusModelManager.getDownloadedModels()
        return when (modelSize) {
            CactusModelSize.SEVEN_B -> downloadedModels.any { it in listOf("qwen3-0.6", "gemma3-270m") }
            CactusModelSize.THIRTEEN_B -> downloadedModels.any { it in listOf("qwen3-1.7", "gemma3-1.5") }
        }
    }
    
    /**
     * Get model status
     */
    fun getModelStatus(modelSize: CactusModelSize): ModelStatus {
        val progress = _downloadProgress.value[modelSize]
        
        return when {
            progress != null && progress.status == DownloadStatus.RUNNING -> {
                ModelStatus.Downloading(progress.progress)
            }
            progress != null && progress.status == DownloadStatus.FAILED -> {
                ModelStatus.Error(progress.reason ?: "Download failed")
            }
            progress != null && progress.status == DownloadStatus.COMPLETED -> {
                ModelStatus.Downloaded
            }
            else -> {
                ModelStatus.NotDownloaded
            }
        }
    }
    
    /**
     * Start downloading a model using Cactus SDK
     */
    suspend fun startDownload(modelSize: CactusModelSize): Long {
        val modelSlug = when (modelSize) {
            CactusModelSize.SEVEN_B -> "qwen3-0.6"  // Fast, small model
            CactusModelSize.THIRTEEN_B -> "qwen3-1.7"  // Larger, more capable
        }
        
        val estimatedBytes = when (modelSize) {
            CactusModelSize.SEVEN_B -> 400_000_000L
            CactusModelSize.THIRTEEN_B -> 1_100_000_000L
        }
        
        // Update progress to show downloading
        _downloadProgress.value = _downloadProgress.value + (modelSize to DownloadProgress(
            modelSize = modelSize,
            bytesDownloaded = 0,
            totalBytes = estimatedBytes,
            progress = 0f,
            status = DownloadStatus.RUNNING
        ))
        
        // Start download in background
        scope.launch {
            try {
                // Use Cactus SDK's built-in download
                cactusLM.downloadModel(modelSlug)
                
                // Update progress to completed
                _downloadProgress.value = _downloadProgress.value + (modelSize to DownloadProgress(
                    modelSize = modelSize,
                    bytesDownloaded = estimatedBytes,
                    totalBytes = estimatedBytes,
                    progress = 1.0f,
                    status = DownloadStatus.COMPLETED
                ))
                
                // Save selected model
                context.modelPrefsDataStore.edit { prefs ->
                    prefs[Keys.SELECTED_LLM_MODEL] = modelSlug
                }
            } catch (e: Exception) {
                // Update progress to failed
                _downloadProgress.value = _downloadProgress.value + (modelSize to DownloadProgress(
                    modelSize = modelSize,
                    bytesDownloaded = 0,
                    totalBytes = estimatedBytes,
                    progress = 0f,
                    status = DownloadStatus.FAILED,
                    reason = e.message ?: "Download failed"
                ))
            }
        }
        
        return System.currentTimeMillis() // Return a pseudo-ID
    }
    
    /**
     * Cancel a download - Note: Cactus SDK doesn't support cancellation
     */
    suspend fun cancelDownload(modelSize: CactusModelSize) {
        // Cactus SDK doesn't currently support cancellation
        // Just update the UI state
        _downloadProgress.value = _downloadProgress.value - modelSize
    }
    
    /**
     * Delete a downloaded model
     */
    suspend fun deleteModel(modelSize: CactusModelSize) {
        val slugs = when (modelSize) {
            CactusModelSize.SEVEN_B -> listOf("qwen3-0.6", "gemma3-270m")
            CactusModelSize.THIRTEEN_B -> listOf("qwen3-1.7", "gemma3-1.5")
        }
        
        slugs.forEach { slug ->
            if (CactusModelManager.isModelDownloaded(slug)) {
                CactusModelManager.deleteModel(slug)
            }
        }
        
        // Update progress state
        _downloadProgress.value = _downloadProgress.value - modelSize
    }
    
    /**
     * Get the selected LLM model slug
     */
    suspend fun getSelectedLlmModel(): String {
        return context.modelPrefsDataStore.data.first()[Keys.SELECTED_LLM_MODEL] ?: "qwen3-0.6"
    }
    
    /**
     * Download STT (speech-to-text) model
     */
    suspend fun downloadSttModel(modelSlug: String = "whisper-tiny") {
        try {
            cactusSTT.downloadModel(modelSlug)
            context.modelPrefsDataStore.edit { prefs ->
                prefs[Keys.SELECTED_STT_MODEL] = modelSlug
            }
        } catch (e: Exception) {
            throw Exception("Failed to download STT model: ${e.message}")
        }
    }
    
    /**
     * Check if STT model is downloaded
     */
    suspend fun isSttModelDownloaded(modelSlug: String = "whisper-tiny"): Boolean {
        return CactusModelManager.isModelDownloaded(modelSlug)
    }
}

data class CactusModel(
    val slug: String,
    val displayName: String,
    val sizeMb: Long,
    val description: String
)

data class DownloadProgress(
    val modelSize: CactusModelSize,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float,
    val status: DownloadStatus,
    val reason: String? = null
)

enum class DownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    UNKNOWN
}

class InsufficientStorageException(message: String) : Exception(message)
