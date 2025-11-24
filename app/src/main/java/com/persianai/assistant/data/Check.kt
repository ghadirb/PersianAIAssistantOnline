package com.persianai.assistant.data

/**
 * مدل داده برای چک (لایه دیتابیس و مدیریت بانکی)
 */
data class Check(
    val id: String,
    val amount: Long,
    val dueDate: Long,
    val recipient: String,
    val bankName: String,
    val checkNumber: String,
    val notes: String = "",
    val isPaid: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
