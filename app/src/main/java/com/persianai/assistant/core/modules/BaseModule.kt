package com.persianai.assistant.core.modules

import android.content.Context
import android.util.Log
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent

abstract class BaseModule(protected val context: Context) {
    abstract val moduleName: String

    protected val TAG: String = this::class.java.simpleName

    abstract suspend fun canHandle(intent: AIIntent): Boolean
    abstract suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult

    protected fun logAction(action: String, details: String = "") {
        try {
            Log.d(TAG, "[$moduleName] $action ${if (details.isBlank()) "" else "| $details"}")
        } catch (_: Exception) {
        }
    }

    protected fun createResult(
        text: String,
        intentName: String,
        success: Boolean = true,
        actionType: String? = null,
        actionData: String? = null,
        spokenOutput: String? = null
    ) = AIIntentResult(
        text = text,
        intentName = intentName,
        success = success,
        actionType = actionType,
        actionData = actionData,
        spokenOutput = spokenOutput
    )
}
