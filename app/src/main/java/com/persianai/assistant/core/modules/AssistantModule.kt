package com.persianai.assistant.core.modules

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AssistantChatIntent

class AssistantModule(private val context: Context) : BaseModule(context) {
    override val moduleName: String = "Assistant"
    
    private val assistant = AdvancedPersianAssistant(context)

    override suspend fun canHandle(intent: AssistantChatIntent): Boolean {
        return true
    }

    override suspend fun execute(request: AIIntentRequest, intent: AssistantChatIntent): AIIntentResult {
        logAction("CHAT", "rawText=${intent.rawText.take(50)}...")
        
        return try {
            val result = try {
                assistant.processRequestWithAI(intent.rawText, intent.moduleId)
            } catch (_: Exception) {
                assistant.processRequest(intent.rawText)
            }

            val debug = mutableMapOf<String, String>()
            debug["actionType"] = result.actionType?.name.orEmpty()
            debug["mode"] = request.workingModeName.orEmpty()

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
