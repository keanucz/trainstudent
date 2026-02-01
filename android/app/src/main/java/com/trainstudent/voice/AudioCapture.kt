package com.trainstudent.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class AudioCapture(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var onSilenceDetected: (() -> Unit)? = null
    private var silenceFrames = 0
    private val silenceThreshold = 500 // Adjust based on testing
    private val maxSilenceFrames = 50 // ~1 second at typical frame rates

    @SuppressLint("MissingPermission")
    fun startCapture(onAudioChunk: (ShortArray) -> Unit) {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    onAudioChunk(chunk)
                    checkSilence(chunk)
                }
            }
        }
    }

    private fun checkSilence(buffer: ShortArray) {
        val maxAmplitude = buffer.maxOfOrNull { abs(it.toInt()) } ?: 0
        if (maxAmplitude < silenceThreshold) {
            silenceFrames++
            if (silenceFrames >= maxSilenceFrames) {
                onSilenceDetected?.invoke()
                silenceFrames = 0
            }
        } else {
            silenceFrames = 0
        }
    }

    fun onSilenceDetected(callback: () -> Unit) {
        onSilenceDetected = callback
    }

    fun stopCapture() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null
    }
}
