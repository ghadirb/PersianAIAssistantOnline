package com.persianai.assistant.data

/**
 * مدل داده برای قسط
 */
data class Installment(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val dueDate: String,
    var isPaid: Boolean
)
