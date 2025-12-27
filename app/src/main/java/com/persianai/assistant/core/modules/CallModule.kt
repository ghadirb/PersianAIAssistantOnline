package com.persianai.assistant.core.modules

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent
import com.persianai.assistant.core.intent.CallSmartIntent

class CallModule(context: Context) : BaseModule(context) {
    override val moduleName: String = "Call"

    override suspend fun canHandle(intent: AIIntent): Boolean {
        return intent is CallSmartIntent
    }

    override suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult {
        return when (intent) {
            is CallSmartIntent -> handleSmartCall(request, intent)
            else -> createResult("نوع Intent نشناخته‌شده", intent.name, false)
        }
    }

    private suspend fun handleSmartCall(request: AIIntentRequest, intent: CallSmartIntent): AIIntentResult {
        val contactName = intent.contactName ?: extractContactName(intent.rawText)
        
        logAction("SMART_CALL", "contact=$contactName")
        
        if (contactName.isBlank()) {
            return createResult(
                text = "⚠️ لطفاً نام مخاطب را مشخص کنید.\nمثال: 'تماس با علی' یا 'تماس با بهمن'",
                intentName = intent.name
            )
        }
        
        return try {
            val phoneNumber = findContactNumber(contactName)
            
            if (phoneNumber != null) {
                // نمایش تأیید قبل از تماس
                return createResult(
                    text = "☎️ آماده تماس با $contactName\nشماره: $phoneNumber\n\nآیا تأیید می‌کنید؟",
                    intentName = intent.name,
                    actionType = "confirm_call",
                    actionData = phoneNumber
                )
            } else {
                return createResult(
                    text = "❌ مخاطب '$contactName' در لیست تماس‌های شما یافت نشد.",
                    intentName = intent.name,
                    success = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling smart call", e)
            createResult(
                text = "❌ خطا: ${e.message}",
                intentName = intent.name,
                success = false
            )
        }
    }

    private fun extractContactName(text: String): String {
        val patterns = listOf(
            "تماس با (\\S+)",
            "برای تماس با (\\S+)",
            "تماس شماره (\\S+)",
            "call to (\\S+)",
            "call (\\S+)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val result = regex.find(text)
            if (result != null) {
                return result.groupValues[1]
            }
        }
        
        return text.trim()
    }

    private fun findContactNumber(contactName: String): String? {
        return try {
            val projection = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            
            val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")
            
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    it.getString(numberIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding contact", e)
            null
        }
    }

    fun initiateCall(phoneNumber: String) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating call", e)
        }
    }
}