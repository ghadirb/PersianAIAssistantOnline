package com.persianai.assistant.core.modules

import android.content.Context
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent

abstract class BaseModule(protected val context: Context) {
    abstract val moduleName: String

    abstract suspend fun canHandle(intent: AIIntent): Boolean
    abstract suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult

    protected fun createResult(
        text: String,
        intentName: String,
        success: Boolean = true,
        actionType: String? = null,
        actionData: String? = null
    ) = AIIntentResult(
        text = text,
        intentName = intentName,
        success = success,
        actionType = actionType,
        actionData = actionData
    )
}
