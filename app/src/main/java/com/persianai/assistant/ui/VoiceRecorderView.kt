package com.persianai.assistant.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.persianai.assistant.R
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min

/**
 * View ضبط صدا شبیه تلگرام
 */
class VoiceRecorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    interface VoiceRecorderListener {
        fun onRecordingStarted()
        fun onRecordingCompleted(audioFile: File, durationMs: Long)
        fun onRecordingCancelled()
        fun onAmplitudeChanged(amplitude: Int)
    }
    
    private var listener: VoiceRecorderListener? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recordingStartTime = 0L
    private var isRecording = false
    private var isCancelled = false
    private var slideOffset = 0f
    private var amplitude = 0
    
    // UI Elements
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val micIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Animation
    private var pulseAnimator: ValueAnimator? = null
    private var pulseRadius = 0f
    private val amplitudes = mutableListOf<Float>()
    private val maxAmplitudes = 50
    
    // Colors
    private val recordColor = Color.parseColor("#FF5252")
    private val cancelColor = Color.parseColor("#757575")
    private val textColor = Color.parseColor("#FFFFFF")
    private val waveformColor = Color.parseColor("#4CAF50")
    
    // Handler for amplitude updates
    private val amplitudeHandler = Handler(Looper.getMainLooper())
    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording && mediaRecorder != null) {
                try {
                    val amp = mediaRecorder?.maxAmplitude ?: 0
                    amplitude = amp
                    amplitudes.add(amp.toFloat() / 32768f)
                    if (amplitudes.size > maxAmplitudes) {
                        amplitudes.removeAt(0)
                    }
                    listener?.onAmplitudeChanged(amp)
                    invalidate()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                amplitudeHandler.postDelayed(this, 100)
            }
        }
    }
    
    init {
        textPaint.apply {
            color = textColor
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        
        waveformPaint.apply {
            color = waveformColor
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        micIconPaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 60f
            textAlign = Paint.Align.CENTER
        }
        
        startPulseAnimation()
    }
    
    fun setListener(listener: VoiceRecorderListener) {
        this.listener = listener
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Draw background circle with pulse effect
        paint.color = if (isCancelled) cancelColor else recordColor
        paint.alpha = if (isRecording) (100 + pulseRadius * 1.5f).toInt() else 255
        canvas.drawCircle(centerX - slideOffset, centerY, 80f + pulseRadius, paint)
        
        // Draw mic icon
        canvas.drawText("🎤", centerX - slideOffset, centerY + 20, micIconPaint)
        
        if (isRecording) {
            // Draw waveform
            drawWaveform(canvas, centerX - slideOffset, centerY)
            
            // Draw recording time
            val duration = (System.currentTimeMillis() - recordingStartTime) / 1000
            val minutes = duration / 60
            val seconds = duration % 60
            val timeText = String.format("%02d:%02d", minutes, seconds)
            canvas.drawText(timeText, centerX - slideOffset, centerY - 100, textPaint)
            
            // Draw slide to cancel hint
            if (slideOffset < 100) {
                textPaint.alpha = (255 - slideOffset * 2.5f).toInt()
                textPaint.textSize = 30f
                canvas.drawText("⬅️ بکشید برای لغو", centerX + 150, centerY, textPaint)
                textPaint.alpha = 255
                textPaint.textSize = 40f
            }
            
            // Visual feedback for cancellation
            if (slideOffset > 150) {
                paint.color = cancelColor
                paint.alpha = 200
                canvas.drawCircle(centerX - slideOffset, centerY, 100f, paint)
                textPaint.color = Color.WHITE
                canvas.drawText("❌", centerX - slideOffset, centerY + 15, textPaint)
            }
        }
    }
    
    private fun drawWaveform(canvas: Canvas, centerX: Float, centerY: Float) {
        if (amplitudes.isEmpty()) return
        
        val path = Path()
        val waveWidth = 300f
        val waveHeight = 60f
        val startX = centerX - waveWidth / 2
        
        amplitudes.forEachIndexed { index, amplitude ->
            val x = startX + (index * waveWidth / maxAmplitudes)
            val y = centerY + (amplitude * waveHeight * (if (index % 2 == 0) 1 else -1))
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        waveformPaint.alpha = 150
        canvas.drawPath(path, waveformPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isRecording) {
                    startRecording()
                }
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isRecording) {
                    slideOffset = abs(width / 2f - event.x)
                    isCancelled = slideOffset > 150
                    invalidate()
                }
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isRecording) {
                    if (isCancelled || (System.currentTimeMillis() - recordingStartTime) < 500) {
                        cancelRecording()
                    } else {
                        stopRecording()
                    }
                }
                slideOffset = 0f
                isCancelled = false
                invalidate()
                return true
            }
        }
        return false
    }
    
    private fun startRecording() {
        try {
            // Create audio file
            val audioDir = File(context.cacheDir, "audio")
            if (!audioDir.exists()) audioDir.mkdirs()
            
            audioFile = File(audioDir, "recording_${System.currentTimeMillis()}.m4a")
            
            // Setup MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile?.absolutePath)
                
                prepare()
                start()
            }
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            amplitudes.clear()
            
            // Start amplitude monitoring
            amplitudeHandler.post(amplitudeRunnable)
            
            // Haptic feedback
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            
            listener?.onRecordingStarted()
            invalidate()
            
        } catch (e: IOException) {
            e.printStackTrace()
            listener?.onRecordingCancelled()
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val duration = System.currentTimeMillis() - recordingStartTime
            
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    listener?.onRecordingCompleted(file, duration)
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            listener?.onRecordingCancelled()
        } finally {
            isRecording = false
            amplitudeHandler.removeCallbacks(amplitudeRunnable)
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            invalidate()
        }
    }
    
    private fun cancelRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            // Delete the audio file
            audioFile?.delete()
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRecording = false
            amplitudeHandler.removeCallbacks(amplitudeRunnable)
            listener?.onRecordingCancelled()
            
            // Haptic feedback for cancellation
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            invalidate()
        }
    }
    
    private fun startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 20f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                pulseRadius = animator.animatedValue as Float
                if (isRecording) {
                    invalidate()
                }
            }
            start()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        if (isRecording) {
            cancelRecording()
        }
        amplitudeHandler.removeCallbacks(amplitudeRunnable)
    }
}
