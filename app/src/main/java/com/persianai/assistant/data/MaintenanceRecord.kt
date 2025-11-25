package com.persianai.assistant.data

import com.persianai.assistant.enums.ServiceType
import java.util.UUID

data class MaintenanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: ServiceType,
    val date: Long,
    val cost: Double,
    val description: String,
    val nextDueDate: Long
)
