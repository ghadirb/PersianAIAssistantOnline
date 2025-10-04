package com.persianai.assistant.models

data class Reminder(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val persianDate: String,
    val time: String,
    val repeatType: RepeatType = RepeatType.NONE,
    val isActive: Boolean = true
)

enum class RepeatType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}
