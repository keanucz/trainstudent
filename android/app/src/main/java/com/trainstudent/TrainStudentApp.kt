package com.trainstudent

import android.app.Application
import com.cactus.CactusContextInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrainStudentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Cactus SDK context (required before using any Cactus functionality)
        CactusContextInitializer.initialize(this)
    }
}
