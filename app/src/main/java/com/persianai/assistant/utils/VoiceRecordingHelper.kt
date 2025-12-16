package com.persianai.assistant.utils

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.services.VoiceRecordingService
import kotlinx.coroutines.launch
import java.io.File

/**
 * Helper برای استفاده آسان از VoiceRecordingService
 */
class VoiceRecordingHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private var listener: RecordingListener? = null
    
    interface RecordingListener {
        fun onRecordingComplete(audioFile: File, duration: Long)
        fun onRecordingError(error: String)
    }
    
    fun startRecording() {
        val intent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "START_RECORDING"
        }
        context.startService(intent)
    }
    
    fun stopRecording() {
        val intent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "STOP_RECORDING"
        }
        context.startService(intent)
    }
    
    fun cancelRecording() {
        val intent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "CANCEL_RECORDING"
        }
        context.startService(intent)
    }
    
    fun setListener(listener: RecordingListener) {
        this.listener = listener
    }
}
