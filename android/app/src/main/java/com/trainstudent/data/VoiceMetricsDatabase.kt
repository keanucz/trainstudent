package com.trainstudent.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Voice metrics entity for tracking voice provider performance
 */
@Entity(tableName = "voice_metrics")
data class VoiceMetric(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,           // "SPEECH_RECOGNIZER", "CACTUS_7B", "CACTUS_13B"
    val latencyMs: Long,            // Time from button press to final parsed intent
    val correctionCount: Int,       // Number of fields user corrected
    val fieldsCorrected: String,    // Comma-separated: "origin,destination,time"
    val batteryLevelBefore: Int,    // Battery % before voice input
    val batteryLevelAfter: Int,     // Battery % after voice input
    val batteryDrainPercent: Float, // Calculated drain
    val transcriptLength: Int,      // Length of final transcript
    val wasSuccessful: Boolean,     // Whether intent was complete
    val timestamp: Long             // Unix timestamp
)

/**
 * Aggregated metrics for a voice provider
 */
data class ProviderMetricsSummary(
    val provider: String,
    val totalSessions: Int,
    val avgLatencyMs: Double,
    val avgCorrectionCount: Double,
    val avgAccuracy: Double,        // 1.0 - (avgCorrectionCount / 3)
    val avgBatteryDrain: Double,
    val successRate: Double
)

@Dao
interface VoiceMetricsDao {
    
    @Insert
    suspend fun insert(metric: VoiceMetric): Long

    @Query("SELECT * FROM voice_metrics ORDER BY timestamp DESC")
    fun getAllMetrics(): Flow<List<VoiceMetric>>

    @Query("SELECT * FROM voice_metrics WHERE provider = :provider ORDER BY timestamp DESC")
    fun getMetricsByProvider(provider: String): Flow<List<VoiceMetric>>

    @Query("SELECT * FROM voice_metrics ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMetrics(limit: Int): Flow<List<VoiceMetric>>

    @Query("""
        SELECT 
            provider,
            COUNT(*) as totalSessions,
            AVG(latencyMs) as avgLatencyMs,
            AVG(correctionCount) as avgCorrectionCount,
            AVG(1.0 - (CAST(correctionCount AS REAL) / 3.0)) as avgAccuracy,
            AVG(batteryDrainPercent) as avgBatteryDrain,
            AVG(CASE WHEN wasSuccessful THEN 1.0 ELSE 0.0 END) as successRate
        FROM voice_metrics
        GROUP BY provider
    """)
    fun getProviderSummaries(): Flow<List<ProviderMetricsSummary>>

    @Query("""
        SELECT 
            provider,
            COUNT(*) as totalSessions,
            AVG(latencyMs) as avgLatencyMs,
            AVG(correctionCount) as avgCorrectionCount,
            AVG(1.0 - (CAST(correctionCount AS REAL) / 3.0)) as avgAccuracy,
            AVG(batteryDrainPercent) as avgBatteryDrain,
            AVG(CASE WHEN wasSuccessful THEN 1.0 ELSE 0.0 END) as successRate
        FROM voice_metrics
        WHERE timestamp > :sinceTimestamp
        GROUP BY provider
    """)
    fun getProviderSummariesSince(sinceTimestamp: Long): Flow<List<ProviderMetricsSummary>>

    @Query("DELETE FROM voice_metrics")
    suspend fun deleteAll()

    @Query("DELETE FROM voice_metrics WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}

@Database(entities = [VoiceMetric::class], version = 1, exportSchema = false)
abstract class VoiceMetricsDatabase : RoomDatabase() {
    abstract fun voiceMetricsDao(): VoiceMetricsDao
}
