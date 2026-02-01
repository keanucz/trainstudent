package com.trainstudent.voice

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.trainstudent.data.VoiceMetric
import com.trainstudent.data.VoiceMetricsDao
import com.trainstudent.model.TravelIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects voice input metrics for comparing provider performance.
 * Tracks latency, accuracy (via corrections), and battery drain.
 */
@Singleton
class VoiceMetricsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceMetricsDao: VoiceMetricsDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var sessionStartTime: Long = 0
    private var sessionProvider: String = ""
    private var sessionBatteryBefore: Int = 0
    private var sessionTranscript: String = ""
    
    // Track corrections made by user
    private val corrections = mutableSetOf<String>()
    
    /**
     * Call when voice input session starts
     */
    fun startSession(provider: String) {
        sessionStartTime = System.currentTimeMillis()
        sessionProvider = provider
        sessionBatteryBefore = getBatteryLevel()
        sessionTranscript = ""
        corrections.clear()
    }
    
    /**
     * Update the current transcript (for tracking final length)
     */
    fun updateTranscript(transcript: String) {
        sessionTranscript = transcript
    }
    
    /**
     * Record a user correction
     */
    fun recordCorrection(field: CorrectedField) {
        corrections.add(field.name)
    }
    
    /**
     * Call when voice input session ends and intent is confirmed
     */
    fun endSession(finalIntent: TravelIntent) {
        if (sessionStartTime == 0L) return
        
        val endTime = System.currentTimeMillis()
        val latencyMs = endTime - sessionStartTime
        val batteryAfter = getBatteryLevel()
        val batteryDrain = (sessionBatteryBefore - batteryAfter).coerceAtLeast(0).toFloat()
        
        val metric = VoiceMetric(
            provider = sessionProvider,
            latencyMs = latencyMs,
            correctionCount = corrections.size,
            fieldsCorrected = corrections.joinToString(","),
            batteryLevelBefore = sessionBatteryBefore,
            batteryLevelAfter = batteryAfter,
            batteryDrainPercent = batteryDrain,
            transcriptLength = sessionTranscript.length,
            wasSuccessful = finalIntent.isComplete,
            timestamp = endTime
        )
        
        scope.launch {
            voiceMetricsDao.insert(metric)
        }
        
        // Reset session
        sessionStartTime = 0
        corrections.clear()
    }
    
    /**
     * Cancel the current session without recording
     */
    fun cancelSession() {
        sessionStartTime = 0
        corrections.clear()
    }
    
    private fun getBatteryLevel(): Int {
        val batteryStatus = context.registerReceiver(
            null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
    }
}

enum class CorrectedField {
    ORIGIN,
    DESTINATION,
    TIME
}
