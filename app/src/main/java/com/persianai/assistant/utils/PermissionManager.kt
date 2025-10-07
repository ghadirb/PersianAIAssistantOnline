package com.persianai.assistant.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {
    
    companion object {
        const val REQUEST_ALL_PERMISSIONS = 1001
        const val REQUEST_NOTIFICATION_PERMISSION = 1002
        const val REQUEST_LOCATION_PERMISSION = 1003
        const val REQUEST_MICROPHONE_PERMISSION = 1004
        const val REQUEST_OVERLAY_PERMISSION = 1005
        const val REQUEST_ACCESSIBILITY_PERMISSION = 1006
        const val REQUEST_ALARM_PERMISSION = 1007
        
        // مجوزهای حیاتی برای عملکرد برنامه
        val CRITICAL_PERMISSIONS = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        // مجوزهای اصلی
        val MAIN_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.RECORD_AUDIO
            )
        }
        
        // مجوزهای آب و هوا
        val WEATHER_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        // مجوزهای ارتباطی
        val COMMUNICATION_PERMISSIONS = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        
        // مجوزهای یادآور و آلارم
        val ALARM_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.SCHEDULE_EXACT_ALARM,
                Manifest.permission.USE_EXACT_ALARM
            )
        } else {
            arrayOf()
        }
    }
    
    // بررسی مجوزها
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == 
               PackageManager.PERMISSION_GRANTED
    }
    
    fun hasAllPermissions(permissions: Array<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }
    
    // بررسی مجوزهای حیاتی
    fun hasCriticalPermissions(): Boolean {
        return hasAllPermissions(CRITICAL_PERMISSIONS)
    }
    
    // بررسی مجوز نوتیفیکیشن
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // در نسخه‌های قدیمی نیازی به مجوز نیست
        }
    }
    
    // بررسی مجوز موقعیت مکانی
    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || 
               hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    
    // بررسی مجوز میکروفون
    fun hasMicrophonePermission(): Boolean {
        return hasPermission(Manifest.permission.RECORD_AUDIO)
    }
    
    // بررسی مجوز overlay
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    // بررسی فعال بودن Accessibility Service
    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(context.packageName) == true
    }
    
    // درخواست مجوزها
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        val permissionsToRequest = permissions.filter { !hasPermission(it) }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest, requestCode)
        }
    }
    
    // درخواست همه مجوزهای اصلی
    fun requestMainPermissions(activity: Activity) {
        requestPermissions(activity, MAIN_PERMISSIONS, REQUEST_ALL_PERMISSIONS)
    }
    
    // درخواست مجوز موقعیت مکانی
    fun requestLocationPermission(activity: Activity) {
        if (!hasLocationPermission()) {
            showPermissionDialog(
                activity,
                "مجوز موقعیت مکانی",
                "برای نمایش آب و هوای دقیق منطقه شما، نیاز به دسترسی به موقعیت مکانی داریم.",
                WEATHER_PERMISSIONS,
                REQUEST_LOCATION_PERMISSION
            )
        }
    }
    
    // درخواست مجوز نوتیفیکیشن
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            showPermissionDialog(
                activity,
                "مجوز نوتیفیکیشن",
                "برای ارسال یادآوری‌ها و اطلاع‌رسانی‌ها، نیاز به مجوز نوتیفیکیشن داریم.",
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }
    
    // درخواست مجوز میکروفون
    fun requestMicrophonePermission(activity: Activity) {
        if (!hasMicrophonePermission()) {
            showPermissionDialog(
                activity,
                "مجوز میکروفون",
                "برای استفاده از دستورات صوتی، نیاز به دسترسی به میکروفون داریم.",
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE_PERMISSION
            )
        }
    }
    
    // نمایش دیالوگ توضیحات مجوز
    private fun showPermissionDialog(
        activity: Activity,
        title: String,
        message: String,
        permissions: Array<String>,
        requestCode: Int
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("اجازه دادن") { _, _ ->
                requestPermissions(activity, permissions, requestCode)
            }
            .setNegativeButton("لغو") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    // باز کردن تنظیمات برنامه برای مجوزها
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    // باز کردن تنظیمات Accessibility
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }
    
    // باز کردن تنظیمات Overlay
    fun openOverlaySettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }
    
    // مدیریت نتیجه درخواست مجوزها
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: (List<String>) -> Unit
    ) {
        val deniedPermissions = mutableListOf<String>()
        
        for (i in permissions.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permissions[i])
            }
        }
        
        if (deniedPermissions.isEmpty()) {
            onGranted()
        } else {
            onDenied(deniedPermissions)
        }
    }
    
    // بررسی و درخواست مجوزهای مورد نیاز در startup
    fun checkAndRequestStartupPermissions(activity: Activity) {
        val missingPermissions = mutableListOf<String>()
        
        // بررسی مجوزهای حیاتی
        CRITICAL_PERMISSIONS.forEach { permission ->
            if (!hasPermission(permission)) {
                missingPermissions.add(permission)
            }
        }
        
        // بررسی مجوزهای اصلی
        MAIN_PERMISSIONS.forEach { permission ->
            if (!hasPermission(permission)) {
                missingPermissions.add(permission)
            }
        }
        
        if (missingPermissions.isNotEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle("مجوزهای مورد نیاز")
                .setMessage("برای عملکرد صحیح برنامه، نیاز به مجوزهای زیر داریم:\n\n" +
                          formatPermissionNames(missingPermissions))
                .setPositiveButton("تنظیم مجوزها") { _, _ ->
                    requestPermissions(activity, missingPermissions.toTypedArray(), REQUEST_ALL_PERMISSIONS)
                }
                .setCancelable(false)
                .show()
        }
    }
    
    // فرمت کردن نام مجوزها به فارسی
    private fun formatPermissionNames(permissions: List<String>): String {
        return permissions.map { permission ->
            when (permission) {
                Manifest.permission.INTERNET -> "• اینترنت"
                Manifest.permission.ACCESS_NETWORK_STATE -> "• وضعیت شبکه"
                Manifest.permission.POST_NOTIFICATIONS -> "• اعلان‌ها"
                Manifest.permission.RECORD_AUDIO -> "• میکروفون"
                Manifest.permission.ACCESS_FINE_LOCATION -> "• موقعیت مکانی دقیق"
                Manifest.permission.ACCESS_COARSE_LOCATION -> "• موقعیت مکانی تقریبی"
                Manifest.permission.CALL_PHONE -> "• برقراری تماس"
                Manifest.permission.SEND_SMS -> "• ارسال پیامک"
                Manifest.permission.SCHEDULE_EXACT_ALARM -> "• تنظیم آلارم دقیق"
                else -> "• ${permission.substringAfterLast('.')}"
            }
        }.joinToString("\n")
    }
}
