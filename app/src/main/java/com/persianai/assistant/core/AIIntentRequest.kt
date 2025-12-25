package com.persianai.assistant.core

import com.persianai.assistant.core.intent.AIIntent

data class AIIntentRequest(
    val intent: AIIntent,
    val source: Source,
    val workingModeName: String? = null
) {
    enum class Source {
        UI,
        NOTIFICATION,
        VOICE
    }
}
