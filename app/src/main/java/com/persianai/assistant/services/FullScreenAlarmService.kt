package com.persianai.assistant.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.persianai.assistant.activities.FullScreenAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Service برای نمایش FullScreenAlarm
 * این Service مسئول نمایش Activity تمام‌صفحه است
 */
class FullScreenAlarmService : Service() {
    
    private val TAG = "FullScreenAlarmService"
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📲 onStartCommand called")
        
        if (intent == null) {
            Log.e(TAG, "❌ Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }
        
        try {
            // دریافت WakeLock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "PersianAssistant::AlarmService"
            ).apply {
                acquire(5 * 60 * 1000L) // 5 دقیقه
            }
            Log.d(TAG, "⚡ WakeLock acquired")
            
            val title = intent.getStringExtra("title") ?: "⏰ یادآوری"
            val description = intent.getStringExtra("description") ?: ""
            val reminderId = intent.getStringExtra("smart_reminder_id") ?: "unknown"
            
            Log.d(TAG, "📝 Showing alarm: $title | ID: $reminderId")
            
            // نمایش Activity
            showFullScreenActivity(title, description, reminderId)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onStartCommand", e)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    private fun showFullScreenActivity(title: String, description: String, reminderId: String) {
        serviceScope.launch {
            try {
                // delay کوچک برای اطمینان از آمادگی
                delay(300)
                
                val intent = Intent(this@FullScreenAlarmService, FullScreenAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("title", title)
                    putExtra("description", description)
                    putExtra("smart_reminder_id", reminderId)
                }
                
                Log.d(TAG, "🎬 Starting FullScreenAlarmActivity")
                startActivity(intent)
                
                // اگر Activity شروع شد، service را بند کنید
                delay(1000)
                stopSelf()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing activity", e)
                stopSelf()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destroyed")
        
        serviceScope.cancel()
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "⚡ WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakelock", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}