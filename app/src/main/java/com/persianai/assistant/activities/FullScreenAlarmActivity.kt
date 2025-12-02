package com.persianai.assistant.activities

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.GestureDetectorCompat
import com.persianai.assistant.R
import com.persianai.assistant.utils.SmartReminderManager
import kotlin.math.abs

/**
 * Activity تمام‌صفحه با سوایپ بهبود شده
 * - سوایپ راست: انجام شد ✅
 * - سوایپ چپ: تعویق ⏰
 */
class FullScreenAlarmActivity : Activity() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var smartReminderId: String? = null
    private val TAG = "FullScreenAlarm"
    
    private lateinit var rootLayout: LinearLayout
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var leftSwipeHint: TextView
    private lateinit var rightSwipeHint: TextView
    private lateinit var leftIcon: ImageView
    private lateinit var rightIcon: ImageView
    
    private var isActionTaken = false
    private var currentSwipeDirection = 0 // 0=none, 1=left, 2=right
    private var swipeProgress = 0f
    
    private val MIN_SWIPE_DISTANCE = 100
    private val MIN_SWIPE_VELOCITY = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 onCreate started")
        
        try {
            setupWindow()
            setContentView(R.layout.activity_full_screen_alarm)
            
            initializeViews()
            setupGestureDetector()
            setupUI()
            startAlarmEffects()
            showSwipeHints()
            
            Log.d(TAG, "✅ onCreate completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate", e)
            finish()
        }
    }
    
    /**
     * تنظیمات پنجره برای نمایش روی lock screen و پس‌زمینه
     */
    private fun setupWindow() {
        // تنظیمات اساسی
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // تنظیمات پنجره قدیمی تر
        @Suppress("DEPRECATION")
        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            
            // برای Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        
        // درخواست dismiss keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        Log.d(TAG, "✅ Window setup complete")
    }
    
    private fun initializeViews() {
        try {
            rootLayout = findViewById(R.id.alarm_root)
            leftSwipeHint = findViewById(R.id.left_swipe_hint)
            rightSwipeHint = findViewById(R.id.right_swipe_hint)
            leftIcon = findViewById(R.id.left_icon)
            rightIcon = findViewById(R.id.right_icon)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views", e)
        }
    }
    
    /**
     * تنظیم Gesture Detector برای شناسایی سوایپ
     */
    private fun setupGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            
            override fun onDown(e: MotionEvent): Boolean {
                currentSwipeDirection = 0
                return true
            }
            
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isActionTaken || e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // اگر حرکت افقی بیشتر از عمودی است
                if (abs(diffX) > abs(diffY)) {
                    swipeProgress = diffX / rootLayout.width
                    
                    if (diffX > 0) {
                        currentSwipeDirection = 2 // راست
                        showRightSwipeIndicator(swipeProgress)
                    } else {
                        currentSwipeDirection = 1 // چپ
                        showLeftSwipeIndicator(abs(swipeProgress))
                    }
                    return true
                }
                return false
            }
            
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (isActionTaken || e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // بررسی که سوایپ افقی است
                if (abs(diffX) > abs(diffY) && abs(velocityX) > MIN_SWIPE_VELOCITY) {
                    if (abs(diffX) > MIN_SWIPE_DISTANCE) {
                        if (diffX > 0) {
                            Log.d(TAG, "👉 Swipe right - Dismiss")
                            onSwipeRight()
                            return true
                        } else {
                            Log.d(TAG, "👈 Swipe left - Snooze")
                            onSwipeLeft()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }
    
    private fun showRightSwipeIndicator(progress: Float) {
        try {
            if (progress > 0.1) {
                rightSwipeHint.visibility = View.VISIBLE
                rightSwipeHint.alpha = minOf(progress * 2, 1f)
                rightIcon.visibility = View.VISIBLE
                rightIcon.alpha = minOf(progress * 2, 1f)
            } else {
                rightSwipeHint.visibility = View.GONE
                rightIcon.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing right indicator", e)
        }
    }
    
    private fun showLeftSwipeIndicator(progress: Float) {
        try {
            if (progress > 0.1) {
                leftSwipeHint.visibility = View.VISIBLE
                leftSwipeHint.alpha = minOf(progress * 2, 1f)
                leftIcon.visibility = View.VISIBLE
                leftIcon.alpha = minOf(progress * 2, 1f)
            } else {
                leftSwipeHint.visibility = View.GONE
                leftIcon.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing left indicator", e)
        }
    }
    
    /**
     * تنظیم UI
     */
    private fun setupUI() {
        val title = intent.getStringExtra("title") ?: "یادآوری"
        val description = intent.getStringExtra("description") ?: ""
        smartReminderId = intent.getStringExtra("smart_reminder_id")
        
        Log.d(TAG, "📝 Title: $title, SmartID: $smartReminderId")
        
        try {
            findViewById<TextView>(R.id.alarm_title)?.text = title
            findViewById<TextView>(R.id.alarm_description)?.apply {
                text = description
                visibility = if (description.isNotEmpty()) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up UI", e)
        }
        
        // تنظیم touch listener برای کل صفحه
        rootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }
    
    /**
     * نمایش راهنمای سوایپ
     */
    private fun showSwipeHints() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // انیمیشن برای نشان دادن که می‌توان سوایپ کرد
                val animator = ObjectAnimator.ofFloat(rootLayout, "translationX", 0f, 30f, 0f, -30f, 0f)
                animator.duration = 3000
                animator.interpolator = AccelerateDecelerateInterpolator()
                animator.repeatCount = 2
                animator.start()
                
                Log.d(TAG, "✅ Swipe hints animation started")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing swipe hints", e)
            }
        }, 1500)
    }
    
    /**
     * رویداد سوایپ به راست (انجام شد)
     */
    private fun onSwipeRight() {
        if (isActionTaken) return
        isActionTaken = true
        
        Log.d(TAG, "✅ Dismissing - Swipe Right")
        
        // انیمیشن خروج به راست
        val animator = ObjectAnimator.ofFloat(rootLayout, "translationX", 0f, rootLayout.width.toFloat())
        animator.duration = 500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
        
        markAsDone()
        
        Handler(Looper.getMainLooper()).postDelayed({
            stopAlarm()
            finish()
        }, 500)
    }
    
    /**
     * رویداد سوایپ به چپ (به تعویق انداختن)
     */
    private fun onSwipeLeft() {
        if (isActionTaken) return
        isActionTaken = true
        
        Log.d(TAG, "⏰ Snoozing - Swipe Left")
        
        // انیمیشن خروج به چپ
        val animator = ObjectAnimator.ofFloat(rootLayout, "translationX", 0f, -rootLayout.width.toFloat())
        animator.duration = 500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
        
        snoozeReminder()
        
        Handler(Looper.getMainLooper()).postDelayed({
            stopAlarm()
            finish()
        }, 500)
    }
    
    /**
     * شروع صدا و لرزش
     */
    private fun startAlarmEffects() {
        startAlarmSound()
        startVibration()
    }
    
    /**
     * شروع صدای آلارم
     */
    private fun startAlarmSound() {
        try {
            Log.d(TAG, "🔊 Starting alarm sound")
            
            val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                
                if (currentVolume < maxVolume * 0.8) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        (maxVolume * 0.8).toInt(),
                        AudioManager.FLAG_SHOW_UI
                    )
                }
            }
            
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                    setAudioAttributes(audioAttributes)
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                setDataSource(this@FullScreenAlarmActivity, alarmUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                
                setOnPreparedListener {
                    it.start()
                    Log.d(TAG, "✅ Alarm sound started")
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting alarm sound", e)
        }
    }
    
    /**
     * شروع لرزش
     */
    private fun startVibration() {
        try {
            Log.d(TAG, "📳 Starting vibration")
            
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 1500, 500, 1500, 500, 1500)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, 0)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
                Log.d(TAG, "✅ Vibration started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting vibration", e)
        }
    }
    
    /**
     * علامت‌گذاری به عنوان انجام شده
     */
    private fun markAsDone() {
        if (smartReminderId != null) {
            try {
                Log.d(TAG, "✅ Marking as done: $smartReminderId")
                val mgr = SmartReminderManager(this)
                mgr.completeReminder(smartReminderId!!)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error marking as done", e)
            }
        }
    }
    
    /**
     * به تعویق انداختن
     */
    private fun snoozeReminder() {
        if (smartReminderId != null) {
            try {
                Log.d(TAG, "⏰ Snoozing: $smartReminderId")
                val mgr = SmartReminderManager(this)
                mgr.snoozeReminder(smartReminderId!!, 5)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error snoozing", e)
            }
        }
    }
    
    /**
     * توقف صدا و لرزش
     */
    private fun stopAlarm() {
        try {
            Log.d(TAG, "🛑 Stopping alarm")
            
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            
            vibrator?.cancel()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping alarm", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔚 onDestroy")
        stopAlarm()
    }
    
    override fun onBackPressed() {
        // جلوگیری از بسته شدن با دکمه Back
        Log.d(TAG, "🚫 Back button blocked")
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // ارسال تمام touch events به gesture detector
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
