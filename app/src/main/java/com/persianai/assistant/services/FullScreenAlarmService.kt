package com.persianai.assistant.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Simple Service - فقط برای manifest
 * ReminderReceiver مسئول نمایش است
 */
class FullScreenAlarmService : Service() {
    
    private val TAG = "FullScreenAlarmService"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}