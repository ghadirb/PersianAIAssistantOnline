package com.persianai.assistant.core.modules

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
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
            val phoneNumbers = findContactNumbers(contactName)
            
            if (phoneNumbers.isNotEmpty()) {
                val numbersText = phoneNumbers.joinToString(separator = "\n") { "• $it" }
                return createResult(
                    text = "☎️ آماده تماس با $contactName\n$numbersText\n\nیک شماره را انتخاب و تأیید کنید.",
                    intentName = intent.name,
                    actionType = "confirm_call",
                    actionData = phoneNumbers.joinToString(separator = "|")
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

    private fun findContactNumbers(contactName: String): List<String> {
        // Check READ_CONTACTS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return emptyList()
        }
        
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
            
            val results = mutableListOf<String>()
            cursor?.use {
                val numberIndex = it.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                while (it.moveToNext()) {
                    val num = it.getString(numberIndex)?.trim()
                    if (!num.isNullOrBlank() && !results.contains(num)) {
                        results.add(num)
                    }
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error finding contact", e)
            emptyList()
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