package com.persianai.assistant.services

import android.content.Context
import android.util.Log
import java.io.File

/**
 * ساده‌ترین API برای استفاده voice recording در Activities
 */
class VoiceRecordingHelper(private val context: Context) {
    
    private val TAG = "VoiceRecordingHelper"
    private val recorder = HybridVoiceRecorder(context)
    private var recordingListener: RecordingListener? = null
    
    interface RecordingListener {
        fun onRecordingStarted()
        fun onRecordingCompleted(audioFile: File, durationMs: Long)
        fun onRecordingCancelled()
        fun onRecordingError(error: String)
    }
    
    init {
        // Setup internal listener
        recorder.setListener(object : HybridVoiceRecorder.RecorderListener {
            override fun onRecordingStarted() {
                Log.d(TAG, "Recording started")
                recordingListener?.onRecordingStarted()
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                Log.d(TAG, "Recording completed")
                recordingListener?.onRecordingCompleted(audioFile, durationMs)
            }
            
            override fun onRecordingCancelled() {
                Log.d(TAG, "Recording cancelled")
                recordingListener?.onRecordingCancelled()
            }
            
            override fun onRecordingError(error: String) {
                Log.e(TAG, "Recording error: $error")
                recordingListener?.onRecordingError(error)
            }
            
            override fun onAmplitudeChanged(amplitude: Int) {
                // Just log, don't need to forward to listener
            }
        })
    }
    
    fun setListener(listener: RecordingListener) {
        this.recordingListener = listener
    }
    
    fun startRecording() {
        Log.d(TAG, "Starting recording...")
        recorder.startRecording()
    }
    
    fun stopRecording() {
        Log.d(TAG, "Stopping recording...")
        recorder.stopRecording()
    }
    
    fun cancelRecording() {
        Log.d(TAG, "Cancelling recording...")
        recorder.cancelRecording()
    }
    
    fun isRecording(): Boolean {
        return recorder.isRecordingInProgress()
    }
    
    fun getRecordingDuration(): Long {
        return recorder.getCurrentRecordingDuration()
    }
    
    fun getRecordingFile(): File? {
        return recorder.getRecordingFile()
    }
}
