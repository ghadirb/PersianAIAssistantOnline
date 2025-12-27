package com.persianai.assistant.core.intent

sealed class AIIntent(open val rawText: String?) {
    abstract val name: String
}

// ============================================
// ASSISTANT & CHAT INTENTS
// ============================================

data class AssistantChatIntent(
    override val rawText: String,
    val moduleId: String? = null,
    val context: String? = null
) : AIIntent(rawText) {
    override val name: String = "assistant.chat"
}

// ============================================
// REMINDER INTENTS
// ============================================

data class ReminderCreateIntent(
    override val rawText: String,
    val hint: String? = null,
    val type: String? = null
) : AIIntent(rawText) {
    override val name: String = "reminder.create"
}

data class ReminderListIntent(
    override val rawText: String = "",
    val category: String? = null
) : AIIntent(rawText) {
    override val name: String = "reminder.list"
}

data class ReminderDeleteIntent(
    override val rawText: String,
    val reminderId: Long? = null
) : AIIntent(rawText) {
    override val name: String = "reminder.delete"
}

data class ReminderUpdateIntent(
    override val rawText: String,
    val reminderId: Long? = null
) : AIIntent(rawText) {
    override val name: String = "reminder.update"
}

// ============================================
// CALL & CONTACT INTENTS
// ============================================

data class CallSmartIntent(
    override val rawText: String,
    val contactName: String? = null
) : AIIntent(rawText) {
    override val name: String = "call.smart"
}

// ============================================
// EDUCATION INTENTS
// ============================================

data class EducationAskIntent(
    override val rawText: String,
    val topic: String? = null
) : AIIntent(rawText) {
    override val name: String = "education.ask"
}

data class EducationGenerateQuestionIntent(
    override val rawText: String,
    val topic: String? = null,
    val level: String? = null
) : AIIntent(rawText) {
    override val name: String = "education.generate_question"
}

// ============================================
// FINANCE INTENTS
// ============================================

data class FinanceTrackIntent(
    override val rawText: String,
    val type: String? = null
) : AIIntent(rawText) {
    override val name: String = "finance.track"
}

data class FinanceReportIntent(
    override val rawText: String,
    val timeRange: String? = null
) : AIIntent(rawText) {
    override val name: String = "finance.report"
}

// ============================================
// NAVIGATION INTENTS
// ============================================

data class NavigationSearchIntent(
    override val rawText: String,
    val destination: String? = null
) : AIIntent(rawText) {
    override val name: String = "navigation.search"
}

data class NavigationStartIntent(
    override val rawText: String,
    val destination: String? = null
) : AIIntent(rawText) {
    override val name: String = "navigation.start"
}

// ============================================
// WEATHER INTENTS
// ============================================

data class WeatherCheckIntent(
    override val rawText: String,
    val location: String? = null
) : AIIntent(rawText) {
    override val name: String = "weather.check"
}

// ============================================
// MUSIC INTENTS
// ============================================

data class MusicPlayIntent(
    override val rawText: String,
    val query: String? = null
) : AIIntent(rawText) {
    override val name: String = "music.play"
}

// ============================================
// UNKNOWN INTENT
// ============================================

object UnknownIntent : AIIntent(null) {
    override val name: String = "unknown"
}
