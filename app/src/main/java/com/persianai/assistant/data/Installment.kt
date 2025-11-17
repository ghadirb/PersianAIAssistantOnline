package com.persianai.assistant.data

/**
 * مدل داده برای قسط
 */
data class Installment(
    val id: String,
    val title: String,
    val totalAmount: Long,
    val monthlyAmount: Long,
    val startDate: Long,
    val totalMonths: Int,
    val currentMonth: Int = 1,
    val creditor: String = "",
    val notes: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
