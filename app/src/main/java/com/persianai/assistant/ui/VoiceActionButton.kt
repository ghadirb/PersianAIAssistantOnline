package com.persianai.assistant.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.persianai.assistant.R
import com.persianai.assistant.services.UnifiedVoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    interface Listener {
        fun onRecordingStarted()
        fun onRecordingCompleted(audioFile: File, durationMs: Long)
        fun onTranscript(text: String)
        fun onRecordingError(error: String)
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val engine = UnifiedVoiceEngine(context)
    private var isRecording = false
    private var btn: MaterialButton
    private var listener: Listener? = null
    private val TAG = "VoiceActionButton"

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_voice_action_button, this, true)
        btn = view.findViewById(R.id.voice_action_btn)
        btn.setOnClickListener { toggleRecording() }
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    private fun toggleRecording() {
        if (!isRecording) startRecording() else stopRecording()
    }

    private fun checkPermission(): Boolean {
        val perm = android.Manifest.permission.RECORD_AUDIO
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val act = context as? Activity ?: return
        ActivityCompat.requestPermissions(act, arrayOf(android.Manifest.permission.RECORD_AUDIO), 4001)
    }

    private fun startRecording() {
        if (!checkPermission()) {
            requestPermission()
            Toast.makeText(context, "نیاز به مجوز میکروفون", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "🎤 Starting recording...")
                val res = engine.startRecording()
                if (res.isSuccess) {
                    isRecording = true
                    btn.text = "⏹️ توقف"
                    listener?.onRecordingStarted()
                    Log.d(TAG, "✅ Recording started")
                } else {
                    val error = res.exceptionOrNull()?.message ?: "خطا نامشخص"
                    listener?.onRecordingError(error)
                    Log.e(TAG, "❌ Failed to start: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in startRecording", e)
                listener?.onRecordingError(e.message ?: "خطا نامشخص")
            }
        }
    }

    private fun stopRecording() {
        scope.launch {
            try {
                Log.d(TAG, "🛑 Stopping recording...")
                val stopRes = engine.stopRecording()
                isRecording = false
                btn.text = "🎤 صحبت کن"
                
                if (stopRes.isSuccess) {
                    val rec = stopRes.getOrNull()
                    if (rec != null) {
                        Log.d(TAG, "✅ Recording stopped: ${rec.file.absolutePath}")
                        listener?.onRecordingCompleted(rec.file, rec.duration)
                        
                        // تحلیل Hybrid و ارسال متن
                        Log.d(TAG, "🔍 Performing hybrid analysis...")
                        val analysis = engine.analyzeHybrid(rec.file)
                        if (analysis.isSuccess) {
                            val result = analysis.getOrNull()
                            if (result != null) {
                                val primary = result.primaryText
                                Log.d(TAG, "✅ Hybrid analysis done: $primary")
                                if (!primary.isNullOrBlank()) {
                                    listener?.onTranscript(primary)
                                } else {
                                    listener?.onRecordingError("تحلیل خالی شد")
                                }
                            } else {
                                listener?.onRecordingError("نتیجه تحلیل خالی است")
                            }
                        } else {
                            val error = analysis.exceptionOrNull()?.message ?: "خطا در تحلیل"
                            Log.e(TAG, "❌ Analysis failed: $error")
                            listener?.onRecordingError(error)
                        }
                    } else {
                        listener?.onRecordingError("نتیجه ضبط خالی است")
                    }
                } else {
                    val error = stopRes.exceptionOrNull()?.message ?: "خطا نامشخص"
                    listener?.onRecordingError(error)
                    Log.e(TAG, "❌ Failed to stop: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in stopRecording", e)
                listener?.onRecordingError(e.message ?: "خطا نامشخص")
            }
        }
    }
}
