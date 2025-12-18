package com.persianai.assistant.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
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
            val res = withContext(Dispatchers.Main) { engine.startRecording() }
            if (res.isSuccess) {
                isRecording = true
                btn.text = "درحال ضبط..."
                listener?.onRecordingStarted()
            } else {
                listener?.onRecordingError(res.exceptionOrNull()?.message ?: "خطا در شروع ضبط")
            }
        }
    }

    private fun stopRecording() {
        scope.launch {
            val stopRes = withContext(Dispatchers.Main) { engine.stopRecording() }
            isRecording = false
            btn.text = context.getString(R.string.app_name)
            val rec = stopRes.getOrNull()
            if (rec != null) {
                listener?.onRecordingCompleted(rec.file, rec.duration)
                // Try hybrid analysis and deliver primary text
                val analysis = engine.analyzeHybrid(rec.file)
                val primary = analysis.getOrNull()?.primaryText
                if (!primary.isNullOrBlank()) listener?.onTranscript(primary)
            } else {
                listener?.onRecordingError(stopRes.exceptionOrNull()?.message ?: "خطا در توقف ضبط")
            }
        }
    }
}
