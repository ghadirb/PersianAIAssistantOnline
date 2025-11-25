package com.persianai.assistant.models

import java.util.UUID

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    var message: String,
    var timestamp: Long,
    var isRepeating: Boolean = false,
    var isCompleted: Boolean = false,
    var tags: List<String> = emptyList(),
    var locationName: String? = null
)

