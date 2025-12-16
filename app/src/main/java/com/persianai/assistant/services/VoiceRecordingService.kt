package com.persianai.assistant.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * سرویس صوتی پس‌زمینه - برای ضبط صدا بدون کرش
 * استفاده: HybridVoiceRecorder درون LifecycleService
 */
class VoiceRecordingService : LifecycleService() {
    
    private val TAG = "VoiceRecordingService"
    private val binder = VoiceRecordingBinder()
    private var voiceRecorder: HybridVoiceRecorder? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    interface VoiceRecordingListener {
        fun onRecordingComplete(audioFile: File, duration: Long)
        fun onRecordingError(error: String)
    }
    
    private var recordingListener: VoiceRecordingListener? = null
    
    inner class VoiceRecordingBinder : Binder() {
        fun getService() = this@VoiceRecordingService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎤 VoiceRecordingService created")
        
        // آماده‌سازی HybridVoiceRecorder
        voiceRecorder = HybridVoiceRecorder(this, serviceScope)
        voiceRecorder?.setListener(object : HybridVoiceRecorder.RecorderListener {
            override fun onRecordingStarted() {
                Log.d(TAG, "✅ Recording started")
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                Log.d(TAG, "✅ Recording complete: ${audioFile.absolutePath}")
                recordingListener?.onRecordingComplete(audioFile, durationMs)
            }
            
            override fun onRecordingCancelled() {
                Log.d(TAG, "❌ Recording cancelled")
            }
            
            override fun onRecordingError(error: String) {
                Log.e(TAG, "❌ Recording error: $error")
                recordingListener?.onRecordingError(error)
            }
            
            override fun onAmplitudeChanged(amplitude: Int) {
                // Silent
            }
        })
        
        // مدیریت صدا برای ضبط بهتر
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            "START_RECORDING" -> startRecording()
            "STOP_RECORDING" -> stopRecording()
            "CANCEL_RECORDING" -> cancelRecording()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }
    
    fun startRecording() {
        try {
            Log.d(TAG, "🔴 START_RECORDING")
            voiceRecorder?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting recording", e)
        }
    }
    
    fun stopRecording() {
        try {
            Log.d(TAG, "⏹️ STOP_RECORDING")
            voiceRecorder?.stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording", e)
        }
    }
    
    fun cancelRecording() {
        try {
            Log.d(TAG, "❌ CANCEL_RECORDING")
            voiceRecorder?.cancelRecording()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling recording", e)
        }
    }
    
    fun setRecordingListener(listener: VoiceRecordingListener) {
        recordingListener = listener
    }
    
    fun isRecording(): Boolean {
        return voiceRecorder?.isRecordingInProgress() ?: false
    }
    
    fun getRecordingDuration(): Long {
        return voiceRecorder?.getCurrentRecordingDuration() ?: 0
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🎤 VoiceRecordingService destroyed")
        serviceScope.cancel()
    }
}
