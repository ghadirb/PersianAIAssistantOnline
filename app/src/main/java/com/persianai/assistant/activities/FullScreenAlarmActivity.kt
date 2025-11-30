package com.persianai.assistant.activities

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.util.Log
import com.persianai.assistant.R
import com.persianai.assistant.utils.SmartReminderManager

/**
 * فعالیت تمام‌صفحه برای نمایش هشدار یادآوری
 */
class FullScreenAlarmActivity : Activity() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var smartReminderId: String? = null
    private val TAG = "FullScreenAlarm"
    private lateinit var gestureDetector: GestureDetector
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "onCreate called")
        
        try {
            // تنظیمات برای نمایش بر روی lock screen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                )
            }
            
            setContentView(R.layout.activity_full_screen_alarm)
            
            // دریافت داده‌ها
            val title = intent.getStringExtra("title") ?: "یادآوری"
            val description = intent.getStringExtra("description") ?: ""
            smartReminderId = intent.getStringExtra("smart_reminder_id")
            
            Log.d(TAG, "Title: $title, Description: $description, SmartReminderId: $smartReminderId")
            
            // تنظیم UI
            val titleTextView = findViewById<TextView>(R.id.alarm_title)
            val descTextView = findViewById<TextView>(R.id.alarm_description)
            val btnDismiss = findViewById<Button>(R.id.btn_dismiss)
            val btnSnooze = findViewById<Button>(R.id.btn_snooze)
            val rootView = findViewById<View>(R.id.alarm_root)
            
            titleTextView?.text = title
            descTextView?.text = description
            
            // دکمه‌ها
            btnDismiss?.setOnClickListener {
                Log.d(TAG, "Dismiss button clicked")
                markAsDone()
                stopAlarm()
                finish()
            }
            
            btnSnooze?.setOnClickListener {
                Log.d(TAG, "Snooze button clicked")
                snoozeReminder()
                stopAlarm()
                finish()
            }
            
            // ژست کشیدن برای انجام/تعویق
            if (rootView != null) {
                setupSwipeGestures(rootView)
            } else {
                Log.w(TAG, "rootView is null, skipping swipe gestures setup")
            }
            
            // شروع صدا و لرزش
            startAlarmSound()
            startVibration()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }
    
    private fun startAlarmSound() {
        try {
            Log.d(TAG, "Starting alarm sound")
            
            // تنظیم صدای سیستم برای ALARM
            val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                
                Log.d(TAG, "Current volume: $currentVolume, Max volume: $maxVolume")
                
                // صدا را به حداکثر تنظیم کن
                if (currentVolume < maxVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                }
            }
            
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setAudioAttributes(audioAttributes)
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                
                setDataSource(this@FullScreenAlarmActivity, alarmUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                setOnPreparedListener { mp -> mp.start() }
                prepareAsync()
            }
            
            Log.d(TAG, "Alarm sound started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm sound", e)
        }
    }
    
    private fun startVibration() {
        try {
            Log.d(TAG, "Starting vibration")
            
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
                }
                Log.d(TAG, "Vibration started successfully")
            } else {
                Log.w(TAG, "Device does not have vibrator")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }
    
    private fun markAsDone() {
        if (smartReminderId != null) {
            try {
                Log.d(TAG, "Marking reminder as done: $smartReminderId")
                val mgr = SmartReminderManager(this)
                mgr.completeReminder(smartReminderId!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking reminder as done", e)
            }
        }
    }
    
    private fun snoozeReminder() {
        if (smartReminderId != null) {
            try {
                Log.d(TAG, "Snoozing reminder: $smartReminderId")
                val mgr = SmartReminderManager(this)
                mgr.snoozeReminder(smartReminderId!!, 5)
            } catch (e: Exception) {
                Log.e(TAG, "Error snoozing reminder", e)
            }
        }
    }
    
    private fun stopAlarm() {
        try {
            Log.d(TAG, "Stopping alarm")
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm", e)
        }
    }
    
    private fun setupSwipeGestures(rootView: View) {
        gestureDetector = GestureDetector(this, object : GestureDetector.OnGestureListener {
            private val SWIPE_THRESHOLD = 150
            private val SWIPE_VELOCITY_THRESHOLD = 150

            override fun onDown(e: MotionEvent): Boolean = false

            override fun onShowPress(e: MotionEvent) {}

            override fun onSingleTapUp(e: MotionEvent): Boolean = false

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean = false

            override fun onLongPress(e: MotionEvent) {}

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)
                    && kotlin.math.abs(diffX) > SWIPE_THRESHOLD
                    && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX > 0) {
                        // سوایپ به راست: انجام شد
                        Log.d(TAG, "Swipe right detected -> complete")
                        markAsDone()
                    } else {
                        // سوایپ به چپ: تعویق
                        Log.d(TAG, "Swipe left detected -> snooze")
                        snoozeReminder()
                    }
                    stopAlarm()
                    finish()
                    return true
                }
                return false
            }
        })

        rootView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        stopAlarm()
    }
    
    override fun onBackPressed() {
        // جلوگیری از بسته شدن با دکمه back
        Log.d(TAG, "Back button pressed - ignored")
    }
}
