package com.trainstudent.voice

sealed class ModelStatus {
    object Ready : ModelStatus()
    object NotDownloaded : ModelStatus()
    object Downloaded : ModelStatus()
    data class Downloading(val progress: Float) : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}
