package com.persianai.assistant.data

/**
 * مدل داده برای چک
 */
data class Check(
    val id: Long = 0,
    val amount: Double,
    val dueDate: String,
    val recipient: String,
    val description: String,
    var status: String
)
