package com.persianai.assistant.core.modules

import android.content.Context
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.ReminderCreateIntent
import com.persianai.assistant.core.intent.ReminderListIntent

class ReminderModule(private val context: Context) {
    private val assistant = AdvancedPersianAssistant(context)

    fun handleCreate(req: AIIntentRequest, intent: ReminderCreateIntent): AIIntentResult {
        val result = assistant.processRequest(intent.rawText)

        val debug = mutableMapOf<String, String>()
        debug["actionType"] = result.actionType?.name.orEmpty()
        return AIIntentResult(
            text = result.text,
            intentName = intent.name,
            debug = debug
        )
    }

    fun handleList(req: AIIntentRequest, intent: ReminderListIntent): AIIntentResult {
        val result = assistant.processRequest("یادآوری های من")

        val debug = mutableMapOf<String, String>()
        debug["actionType"] = result.actionType?.name.orEmpty()
        return AIIntentResult(
            text = result.text,
            intentName = intent.name,
            debug = debug
        )
    }
}
