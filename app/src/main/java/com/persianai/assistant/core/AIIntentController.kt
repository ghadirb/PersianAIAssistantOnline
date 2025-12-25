package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.OfflineIntentParser
import com.persianai.assistant.core.intent.*
import com.persianai.assistant.core.modules.AssistantModule
import com.persianai.assistant.core.modules.ReminderModule
import org.json.JSONObject

class AIIntentController(private val context: Context) {

    private val assistantModule = AssistantModule(context)
    private val reminderModule = ReminderModule(context)

    suspend fun handle(request: AIIntentRequest): AIIntentResult {
        logIntent(request)
        return when (val i = request.intent) {
            is AssistantChatIntent -> assistantModule.handle(request, i)
            is ReminderCreateIntent -> reminderModule.handleCreate(request, i)
            is ReminderListIntent -> reminderModule.handleList(request, i)
            is CallSmartIntent -> AIIntentResult(
                text = "برای تماس هوشمند هنوز کنترلر مرکزی متصل نشده است.",
                intentName = i.name
            )
            is EducationAskIntent -> AIIntentResult(
                text = "ماژول آموزش هنوز به کنترلر مرکزی متصل نشده است.",
                intentName = i.name
            )
            UnknownIntent -> AIIntentResult(
                text = "متوجه منظورت نشدم. لطفاً واضح‌تر بگو.",
                intentName = i.name
            )
        }
    }

    fun detectIntentFromText(text: String, mode: String? = null): AIIntent {
        val t = text.trim()
        if (t.isBlank()) return UnknownIntent

        if (mode == "reminder") {
            return ReminderCreateIntent(rawText = t)
        }

        return try {
            val parser = OfflineIntentParser(context)
            if (parser.canHandle(t)) {
                val json = JSONObject(parser.parse(t))
                when (json.optString("action")) {
                    "reminder" -> ReminderCreateIntent(rawText = t)
                    else -> AssistantChatIntent(rawText = t)
                }
            } else {
                AssistantChatIntent(rawText = t)
            }
        } catch (_: Exception) {
            AssistantChatIntent(rawText = t)
        }
    }

    private fun logIntent(request: AIIntentRequest) {
        try {
            Log.d(
                "AIIntentController",
                "intent=${request.intent.name} source=${request.source} mode=${request.workingModeName.orEmpty()}"
            )
        } catch (_: Exception) {
        }
    }
}
