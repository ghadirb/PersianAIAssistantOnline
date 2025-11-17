package com.persianai.assistant.data

import java.io.Serializable

/**
 * مدل یادآوری پیشرفته
 */
data class AdvancedReminder(
    val id: String,
    val type: ReminderType,
    val title: String,
    val description: String = "",
    val category: String = "شخصی",
    val priority: Priority = Priority.MEDIUM,
    
    // Time-based
    val triggerTime: Long = 0,
    
    // Location-based
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Int = 100, // meters
    val locationName: String = "",
    
    // Recurring
    val isRecurring: Boolean = false,
    val recurringPattern: RecurringPattern = RecurringPattern.NONE,
    val recurringInterval: Long = 0,
    
    // Conditional
    val condition: String = "",
    val conditionType: ConditionType = ConditionType.NONE,
    
    // Attachments
    val attachments: List<String> = emptyList(), // File paths
    
    // Status
    val completed: Boolean = false,
    val completedAt: Long = 0,
    val snoozedUntil: Long = 0,
    
    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList()
) : Serializable {
    
    enum class ReminderType {
        TIME_BASED,        // یادآوری زمانی
        LOCATION_BASED,    // یادآوری مکانی
        CONDITIONAL        // یادآوری شرطی
    }
    
    enum class Priority {
        LOW,     // کم
        MEDIUM,  // متوسط
        HIGH     // بالا
    }
    
    enum class RecurringPattern {
        NONE,          // نامشخص
        DAILY,         // روزانه
        WEEKLY,        // هفتگی
        MONTHLY,       // ماهانه
        YEARLY,        // سالانه
        CUSTOM         // سفارشی
    }
    
    enum class ConditionType {
        NONE,              // بدون شرط
        WEATHER,           // شرایط آب و هوا
        LOCATION_ARRIVAL,  // رسیدن به مکان
        LOCATION_DEPARTURE,// خروج از مکان
        TIME_OF_DAY,       // زمان خاص روز
        DAY_OF_WEEK,       // روز خاص هفته
        CUSTOM             // شرط سفارشی
    }
    
    fun isOverdue(): Boolean {
        return !completed && triggerTime > 0 && triggerTime < System.currentTimeMillis()
    }
    
    fun isUpcoming(): Boolean {
        val oneDayLater = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        return !completed && triggerTime in System.currentTimeMillis()..oneDayLater
    }
    
    fun isSnoozed(): Boolean {
        return snoozedUntil > System.currentTimeMillis()
    }
}
