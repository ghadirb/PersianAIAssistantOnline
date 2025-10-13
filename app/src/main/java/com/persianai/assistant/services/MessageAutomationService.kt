package com.persianai.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * سرویس Accessibility برای ارسال خودکار پیام
 * 
 * توجه: این سرویس نیاز به فعال‌سازی دستی توسط کاربر دارد
 * Settings → Accessibility → Persian AI Assistant → Enable
 */
class MessageAutomationService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingMessage: String? = null
    private var targetApp: String? = null

    companion object {
        private const val TAG = "MessageAutomation"
        
        // Package names
        private const val TELEGRAM = "org.telegram.messenger"
        private const val EITAA = "ir.eitaa.messenger"
        private const val RUBIKA = "ir.resaneh1.iptv"
        private const val WHATSAPP = "com.whatsapp"
        
        var isServiceEnabled = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            packageNames = arrayOf(TELEGRAM, EITAA, RUBIKA, WHATSAPP)
        }
        
        setServiceInfo(info)
        isServiceEnabled = true
        
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        
        Log.d(TAG, "Event: ${event.eventType}, Package: $packageName")
        
        // فقط اگه پیام pending داریم
        if (pendingMessage != null && packageName == targetApp) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handler.postDelayed({
                        tryToSendMessage(packageName)
                    }, 1000) // صبر می‌کنیم تا UI بارگذاری شه
                }
            }
        }
    }

    private fun tryToSendMessage(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val message = pendingMessage ?: return
        
        Log.d(TAG, "Trying to send message: $message in $packageName")
        
        try {
            when (packageName) {
                TELEGRAM -> sendInTelegram(rootNode, message)
                EITAA -> sendInEitaa(rootNode, message)
                RUBIKA -> sendInRubika(rootNode, message)
                WHATSAPP -> sendInWhatsApp(rootNode, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
        }
    }

    private fun sendInTelegram(root: AccessibilityNodeInfo, message: String) {
        // پیدا کردن text field
        val editText = findNodeByClassName(root, "android.widget.EditText")
        
        if (editText != null && editText.isEditable) {
            // وارد کردن متن
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            Log.d(TAG, "Text inserted in Telegram")
            
            // صبر کن تا متن وارد شه
            handler.postDelayed({
                // پیدا کردن دکمه ارسال
                val sendButton = findNodeByContentDesc(root, "Send") 
                    ?: findNodeByText(root, "Send")
                    ?: findNodeByResourceId(root, "send")
                
                if (sendButton != null && sendButton.isClickable) {
                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Send button clicked in Telegram")
                    clearPendingMessage()
                }
            }, 500)
        }
        
        root.recycle()
    }

    private fun sendInEitaa(root: AccessibilityNodeInfo, message: String) {
        // مشابه تلگرام
        val editText = findNodeByClassName(root, "android.widget.EditText")
        
        if (editText != null && editText.isEditable) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            handler.postDelayed({
                val sendButton = findNodeByContentDesc(root, "ارسال")
                    ?: findNodeByText(root, "ارسال")
                
                sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clearPendingMessage()
            }, 500)
        }
        
        root.recycle()
    }

    private fun sendInRubika(root: AccessibilityNodeInfo, message: String) {
        // مشابه
        val editText = findNodeByClassName(root, "android.widget.EditText")
        
        if (editText != null && editText.isEditable) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            handler.postDelayed({
                val sendButton = findNodeByText(root, "ارسال")
                sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clearPendingMessage()
            }, 500)
        }
        
        root.recycle()
    }

    private fun sendInWhatsApp(root: AccessibilityNodeInfo, message: String) {
        val editText = findNodeByResourceId(root, "entry")
        
        if (editText != null && editText.isEditable) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
            editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            handler.postDelayed({
                val sendButton = findNodeByContentDesc(root, "Send")
                sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clearPendingMessage()
            }, 500)
        }
        
        root.recycle()
    }

    // Helper functions
    private fun findNodeByClassName(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (root.className == className) return root
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByClassName(child, className)
            if (result != null) return result
        }
        
        return null
    }

    private fun findNodeByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByContentDesc(child, desc)
            if (result != null) return result
        }
        
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        
        return null
    }

    private fun findNodeByResourceId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (root.viewIdResourceName?.contains(id) == true) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByResourceId(child, id)
            if (result != null) return result
        }
        
        return null
    }

    private fun clearPendingMessage() {
        pendingMessage = null
        targetApp = null
        Log.d(TAG, "Pending message cleared")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceEnabled = false
        Log.d(TAG, "Service destroyed")
    }

    /**
     * تنظیم پیام برای ارسال خودکار
     */
    fun setPendingMessage(app: String, message: String) {
        this.targetApp = app
        this.pendingMessage = message
        Log.d(TAG, "Pending message set: $message for $app")
    }
}
