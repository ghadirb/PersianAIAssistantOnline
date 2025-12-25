package com.persianai.assistant.core.intent

sealed class AIIntent(open val rawText: String?) {
    abstract val name: String
}

data class AssistantChatIntent(
    override val rawText: String,
    val moduleId: String? = null
) : AIIntent(rawText) {
    override val name: String = "assistant.chat"
}

data class ReminderCreateIntent(
    override val rawText: String,
    val hint: String? = null
) : AIIntent(rawText) {
    override val name: String = "reminder.create"
}

data class ReminderListIntent(
    override val rawText: String = ""
) : AIIntent(rawText) {
    override val name: String = "reminder.list"
}

data class CallSmartIntent(
    override val rawText: String,
    val contactName: String? = null
) : AIIntent(rawText) {
    override val name: String = "call.smart"
}

data class EducationAskIntent(
    override val rawText: String,
    val topic: String? = null
) : AIIntent(rawText) {
    override val name: String = "education.ask"
}

object UnknownIntent : AIIntent(null) {
    override val name: String = "unknown"
}
