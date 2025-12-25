package com.persianai.assistant.core

data class AIIntentResult(
    val text: String,
    val intentName: String,
    val debug: Map<String, String> = emptyMap()
)
