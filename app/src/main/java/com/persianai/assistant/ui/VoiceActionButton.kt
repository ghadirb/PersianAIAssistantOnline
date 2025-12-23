package com.persianai.assistant.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.persianai.assistant.R
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

    private var isListening = false
    private var btn: MaterialButton
    private var listener: Listener? = null
    private val TAG = "VoiceActionButton"
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_voice_action_button, this, true)
        btn = view.findViewById(R.id.voice_action_btn)
        btn.setOnClickListener { toggleListening() }
        ensureSpeechRecognizer()
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    private fun toggleListening() {
        if (!isListening) startListening() else stopListening()
    }

    private fun ensureSpeechRecognizer() {
        if (speechRecognizer != null) return
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "SpeechRecognizer not available")
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
                    }

                    override fun onError(error: Int) {
                        Log.w(TAG, "SpeechRecognizer error=$error")
                        isListening = false
                        btn.text = "🎤 صحبت کن"
                        listener?.onRecordingError("خطا در تشخیص گفتار (کد: $error)")
                    }

                    override fun onResults(results: Bundle?) {
                        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = texts?.firstOrNull()?.trim().orEmpty()
                        Log.d(TAG, "SpeechRecognizer results: ${best.take(200)}")
                        isListening = false
                        btn.text = "🎤 صحبت کن"
                        if (best.isNotBlank()) {
                            listener?.onTranscript(best)
                        } else {
                            listener?.onRecordingError("متنی دریافت نشد")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init SpeechRecognizer", e)
        }
    }

    private fun checkPermission(): Boolean {
        val perm = android.Manifest.permission.RECORD_AUDIO
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val act = context as? Activity ?: return
        ActivityCompat.requestPermissions(act, arrayOf(android.Manifest.permission.RECORD_AUDIO), 4001)
    }

    private fun startListening() {
        if (!checkPermission()) {
            requestPermission()
            Toast.makeText(context, "نیاز به مجوز میکروفون", Toast.LENGTH_SHORT).show()
            return
        }

        ensureSpeechRecognizer()
        val sr = speechRecognizer
        if (sr == null) {
            listener?.onRecordingError("تشخیص گفتار روی این دستگاه فعال نیست")
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "صحبت کنید")
            }
            isListening = true
            btn.text = "⏹️ توقف"
            listener?.onRecordingStarted()
            sr.startListening(intent)
            Log.d(TAG, "✅ SpeechRecognizer listening")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception starting SpeechRecognizer", e)
            isListening = false
            btn.text = "🎤 صحبت کن"
            listener?.onRecordingError(e.message ?: "خطا نامشخص")
        }
    }

    private fun stopListening() {
        try {
            isListening = false
            btn.text = "🎤 صحبت کن"
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception stopping SpeechRecognizer", e)
            listener?.onRecordingError(e.message ?: "خطا نامشخص")
        }
    }
 }
