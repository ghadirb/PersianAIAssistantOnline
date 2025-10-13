package com.persianai.assistant.models

data class Expense(
    val id: Long = System.currentTimeMillis(),
    val amount: Long,
    val category: ExpenseCategory,
    val description: String,
    val persianDate: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ExpenseCategory(val displayName: String, val emoji: String) {
    FOOD("خوراک", "🍽️"),
    TRANSPORT("حمل و نقل", "🚗"),
    SHOPPING("خرید", "🛒"),
    HEALTH("بهداشت", "💊"),
    ENTERTAINMENT("سرگرمی", "🎬"),
    BILLS("قبوض", "💳"),
    EDUCATION("آموزش", "📚"),
    OTHER("سایر", "💰")
}
