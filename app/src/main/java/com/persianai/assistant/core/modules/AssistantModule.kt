package com.persianai.assistant.core.modules

import android.content.Context
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AssistantChatIntent

class AssistantModule(private val context: Context) {
    private val assistant = AdvancedPersianAssistant(context)

    suspend fun handle(req: AIIntentRequest, intent: AssistantChatIntent): AIIntentResult {
        val result = try {
            assistant.processRequestWithAI(intent.rawText, intent.moduleId)
        } catch (_: Exception) {
            assistant.processRequest(intent.rawText)
        }

        val debug = mutableMapOf<String, String>()
        debug["actionType"] = result.actionType?.name.orEmpty()

        return AIIntentResult(
            text = result.text,
            intentName = intent.name,
            debug = debug
        )
    }
}
