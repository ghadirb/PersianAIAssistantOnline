package com.persianai.assistant.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * ساده‌ترین API برای استفاده voice recording در Activities
 * Now backed by `UnifiedVoiceEngine` to centralize lifecycle and errors.
 */
class VoiceRecordingHelper(private val context: Context) {

    private val TAG = "VoiceRecordingHelper"
    private val engine = UnifiedVoiceEngine(context)
    private var recordingListener: RecordingListener? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    interface RecordingListener {
        fun onRecordingStarted()
        fun onRecordingCompleted(audioFile: File, durationMs: Long)
        fun onRecordingCancelled()
        fun onRecordingError(error: String)
    }

    fun setListener(listener: RecordingListener) {
        this.recordingListener = listener
    }

    fun startRecording() {
        Log.d(TAG, "Starting recording via UnifiedVoiceEngine...")
        scope.launch {
            val result = engine.startRecording()
            if (result.isSuccess) {
                recordingListener?.onRecordingStarted()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                recordingListener?.onRecordingError(err)
            }
        }
    }

    fun stopRecording() {
        Log.d(TAG, "Stopping recording via UnifiedVoiceEngine...")
        scope.launch {
            val result = engine.stopRecording()
            if (result.isSuccess) {
                val rec = result.getOrNull()
                if (rec != null) {
                    recordingListener?.onRecordingCompleted(rec.file, rec.duration)
                } else {
                    recordingListener?.onRecordingError("Recording result empty")
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                recordingListener?.onRecordingError(err)
            }
        }
    }

    fun cancelRecording() {
        Log.d(TAG, "Cancelling recording via UnifiedVoiceEngine...")
        scope.launch {
            val result = engine.cancelRecording()
            if (result.isSuccess) {
                recordingListener?.onRecordingCancelled()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                recordingListener?.onRecordingError(err)
            }
        }
    }

    fun isRecording(): Boolean = engine.isRecordingInProgress()

    fun getRecordingDuration(): Long = engine.getCurrentRecordingDuration()

    fun getRecordingFile(): File? = engine.getRecordingFile()
}
