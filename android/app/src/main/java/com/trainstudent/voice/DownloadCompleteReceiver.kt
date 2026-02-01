package com.trainstudent.voice

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Broadcast receiver for DownloadManager completion events.
 * Updates model status when downloads complete.
 */
@AndroidEntryPoint
class DownloadCompleteReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var modelDownloader: CactusModelDownloader
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return
        
        // The download has completed - the CactusModelDownloader will automatically
        // detect this through its monitoring loop and update the status
        
        // Optionally, we could send a local broadcast or use an event bus here
        // to immediately notify the UI
    }
}
