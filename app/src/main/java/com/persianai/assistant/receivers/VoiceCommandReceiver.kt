package com.persianai.assistant.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.DashboardActivity
import com.persianai.assistant.core.AIIntentController
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceCommandReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "VoiceCommandReceiver"
        const val ACTION_RUN_COMMAND = "com.persianai.assistant.action.RUN_COMMAND"
        const val ACTION_CANCEL_COMMAND = "com.persianai.assistant.action.CANCEL_COMMAND"
        const val ACTION_EXECUTE_CALL = "com.persianai.assistant.action.EXECUTE_CALL"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        
        private const val CHANNEL_ID = "voice_command_results"
        private const val NOTIFICATION_BASE_ID = 2000
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RUN_COMMAND -> handleRunCommand(context, intent)
            ACTION_CANCEL_COMMAND -> handleCancelCommand(context, intent)
            ACTION_EXECUTE_CALL -> handleExecuteCall(context, intent)
        }
    }
    
    private fun handleRunCommand(context: Context, intent: Intent) {
        val transcript = intent.getStringExtra(EXTRA_TRANSCRIPT)?.trim().orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        
        if (transcript.isBlank()) {
            showErrorNotification(context, "متن خالی است", notificationId)
            return
        }
        
        // Check RECORD_AUDIO permission for future voice commands
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Open app to request permission
            val appIntent = Intent(context, com.persianai.assistant.activities.DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("request_permission", "RECORD_AUDIO")
            }
            context.startActivity(appIntent)
            
            showErrorNotification(context, "برای دستورات صوتی، مجوز ضبط صدا را در برنامه فعال کنید", notificationId)
            return
        }
        
        // Show processing notification
        showProcessingNotification(context, transcript, notificationId)
        
        // Execute command in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val controller = AIIntentController(context)
                val prefs = PreferencesManager(context)
                val intentDetected = controller.detectIntentFromTextAsync(transcript, "notification")
                val result = controller.handle(
                    AIIntentRequest(
                        intent = intentDetected,
                        source = AIIntentRequest.Source.NOTIFICATION,
                        workingModeName = prefs.getWorkingMode().name
                    )
                )
                
                // Show result notification
                showResultNotification(context, transcript, result, notificationId, mode)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
                showErrorNotification(context, "خطا: ${e.message}", notificationId)
            }
        }
    }
    
    private fun handleCancelCommand(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
        
        // Show cancelled notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("❌ فرمان لغو شد")
            .setContentText("فرمان صوتی توسط کاربر لغو گردید")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        nm.notify(notificationId + 1000, notification)
    }
    
    private fun handleExecuteCall(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number")?.trim()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        
        if (phoneNumber.isNullOrBlank()) {
            showErrorNotification(context, "شماره تماس یافت نشد", notificationId)
            return
        }
        
        // Check CALL_PHONE permission
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Open app to request permission
            val appIntent = Intent(context, com.persianai.assistant.activities.DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("request_permission", "CALL_PHONE")
                putExtra("pending_call", phoneNumber)
            }
            context.startActivity(appIntent)
            
            showErrorNotification(context, "برای تماس، مجوز تماس را در برنامه فعال کنید", notificationId)
            return
        }
        
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            
            // Show success notification
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📞 در حال تماس...")
                .setContentText("با شماره $phoneNumber تماس گرفته می‌شود")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            nm.notify(notificationId + 2000, notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error making call", e)
            showErrorNotification(context, "خطا در برقراری تماس: ${e.message}", notificationId)
        }
    }
    
    private fun showProcessingNotification(context: Context, transcript: String, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔄 در حال پردازش فرمان...")
            .setContentText(transcript)
            .setStyle(NotificationCompat.BigTextStyle().bigText(transcript))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        
        nm.notify(notificationId, notification)
    }
    
    private fun showResultNotification(context: Context, transcript: String, result: com.persianai.assistant.core.AIIntentResult, notificationId: Int, mode: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open dashboard
        val dashboardIntent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val dashboardPending = PendingIntent.getActivity(
            context, 
            notificationId + 1, 
            dashboardIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (result.success) "✅ فرمان اجرا شد" else "⚠️ فرمان ناموفق")
            .setContentText(result.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("فرمان: $transcript\n\nنتیجه: ${result.text}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(dashboardPending)
            .setAutoCancel(true)
        
        // Add action buttons for call confirmation if needed
        if (result.actionType == "confirm_call" && result.actionData != null) {
            // Run action (call)
            val runIntent = Intent(context, VoiceCommandReceiver::class.java).apply {
                action = "com.persianai.assistant.action.EXECUTE_CALL"
                putExtra("phone_number", result.actionData.split("|").firstOrNull())
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val runPending = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                runIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_call, "تماس", runPending)
        }
        
        nm.notify(notificationId, builder.build())
    }
    
    private fun showErrorNotification(context: Context, error: String, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("❌ خطا در اجرای فرمان")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        nm.notify(notificationId, notification)
    }
}
