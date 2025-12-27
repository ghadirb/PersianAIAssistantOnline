package com.persianai.assistant.core.modules

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent
import com.persianai.assistant.core.intent.AssistantChatIntent

class AssistantModule(context: Context) : BaseModule(context) {
    override val moduleName: String = "Assistant"
    
    private val assistant = AdvancedPersianAssistant(context)

    override suspend fun canHandle(intent: AIIntent): Boolean {
        return intent is AssistantChatIntent
    }

    override suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult {
        if (intent !is AssistantChatIntent) {
            return createResult(
                text = "نوع Intent نشناخته‌شده",
                intentName = intent.name,
                success = false
            )
        }

        logAction("CHAT", "rawText=${intent.rawText.take(50)}...")
        
        return try {
            val result = try {
                assistant.processRequestWithAI(intent.rawText, intent.moduleId)
            } catch (_: Exception) {
                assistant.processRequest(intent.rawText)
            }

            createResult(
                text = result.text,
                intentName = intent.name,
                actionData = intent.moduleId,
                spokenOutput = result.text
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in assistant chat", e)
            createResult(
                text = "❌ خطا: ${e.message}",
                intentName = intent.name,
                success = false
            )
        }
    }
}
