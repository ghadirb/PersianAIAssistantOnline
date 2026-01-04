package com.persianai.assistant.ai

import android.content.Context
import org.json.JSONObject

/**
 * آفلاین حذف شده؛ صرفاً پاسخ ثابت برمی‌گردد تا online استفاده شود.
 */
class OfflineIntentParser(private val context: Context) {
    fun parse(text: String): String {
        val json = JSONObject()
        json.put("action", "none")
        json.put("message", "حالت آفلاین حذف شده است؛ لطفاً از سرویس آنلاین استفاده کنید.")
        return json.toString()
    }
}
