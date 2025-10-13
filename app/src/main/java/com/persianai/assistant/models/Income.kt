package com.persianai.assistant.models

data class Income(
    val id: Long = System.currentTimeMillis(),
    val amount: Long,
    val source: String,
    val persianDate: String,
    val timestamp: Long = System.currentTimeMillis()
)
