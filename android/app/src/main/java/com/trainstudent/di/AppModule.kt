package com.trainstudent.di

import android.content.Context
import androidx.room.Room
import com.trainstudent.data.VoiceMetricsDatabase
import com.trainstudent.data.VoiceMetricsDao
import com.trainstudent.voice.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpeechRecognizerProvider

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CactusLlmProvider

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVoiceMetricsDatabase(
        @ApplicationContext context: Context
    ): VoiceMetricsDatabase {
        return Room.databaseBuilder(
            context,
            VoiceMetricsDatabase::class.java,
            "voice_metrics.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideVoiceMetricsDao(database: VoiceMetricsDatabase): VoiceMetricsDao {
        return database.voiceMetricsDao()
    }

    @Provides
    @Singleton
    fun provideStationMatcher(): StationMatcher {
        return StationMatcher()
    }

    @Provides
    @Singleton
    fun provideTimeParser(): TimeParser {
        return TimeParser()
    }

    @Provides
    @Singleton
    @SpeechRecognizerProvider
    fun provideSpeechRecognizerHandler(
        @ApplicationContext context: Context,
        stationMatcher: StationMatcher,
        timeParser: TimeParser
    ): VoiceInputHandler {
        return SpeechRecognizerHandler(context, stationMatcher, timeParser)
    }

    @Provides
    @Singleton
    fun provideCactusModelDownloader(
        @ApplicationContext context: Context
    ): CactusModelDownloader {
        return CactusModelDownloader(context)
    }

    @Provides
    @Singleton
    @CactusLlmProvider
    fun provideCactusLlmHandler(
        @ApplicationContext context: Context,
        stationMatcher: StationMatcher,
        timeParser: TimeParser,
        modelDownloader: CactusModelDownloader
    ): VoiceInputHandler {
        return CactusLlmHandler(
            context = context,
            modelDownloader = modelDownloader,
            stationMatcher = stationMatcher,
            timeParser = timeParser
        )
    }

    @Provides
    @Singleton
    fun provideVoiceMetricsCollector(
        @ApplicationContext context: Context,
        dao: VoiceMetricsDao
    ): VoiceMetricsCollector {
        return VoiceMetricsCollector(context, dao)
    }
}
