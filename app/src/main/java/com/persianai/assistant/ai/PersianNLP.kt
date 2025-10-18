package com.persianai.assistant.ai

import java.util.*

class PersianNLP {
    
    data class Command(
        val type: Type,
        val amount: Double? = null,
        val text: String? = null,
        val time: Long? = null
    )
    
    enum class Type { EXPENSE, INCOME, REMINDER, CHECK, UNKNOWN }
    
    fun parse(text: String): Command {
        val t = text.trim()
        
        // مبلغ
        val amount = extractAmount(t)
        
        return when {
            t.contains("خرج") || t.contains("هزینه") || t.contains("پرداخت") -> 
                Command(Type.EXPENSE, amount, t)
            t.contains("درآمد") || t.contains("دریافت") || t.contains("واریز") -> 
                Command(Type.INCOME, amount, t)
            t.contains("یادآوری") || t.contains("یاداوری") || t.contains("بیدار") -> 
                Command(Type.REMINDER, null, t, extractTime(t))
            t.contains("چک") -> 
                Command(Type.CHECK, amount, t)
            else -> Command(Type.UNKNOWN, amount, t)
        }
    }
    
    private fun extractAmount(text: String): Double? {
        // "700 هزار" یا "700000" یا "7 میلیون"
        val patterns = listOf(
            Regex("""(\d+)\s*میلیون"""),
            Regex("""(\d+)\s*هزار"""),
            Regex("""(\d+)""")
        )
        
        patterns.forEach { pattern ->
            pattern.find(text)?.let { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@let
                return when {
                    text.contains("میلیون") -> num * 1_000_000
                    text.contains("هزار") -> num * 1_000
                    else -> num
                }
            }
        }
        return null
    }
    
    private fun extractTime(text: String): Long {
        // "6:47" یا "9 صبح"
        val timePattern = Regex("""(\d{1,2}):(\d{2})""")
        val hourPattern = Regex("""(\d{1,2})\s*(صبح|ظهر|عصر|شب)""")
        
        timePattern.find(text)?.let {
            val hour = it.groupValues[1].toInt()
            val min = it.groupValues[2].toInt()
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
            }.timeInMillis
        }
        
        hourPattern.find(text)?.let {
            val hour = it.groupValues[1].toInt()
            val period = it.groupValues[2]
            val adjustedHour = when(period) {
                "صبح" -> hour
                "ظهر" -> 12 + hour
                "عصر" -> 12 + hour
                "شب" -> if (hour < 12) hour + 12 else hour
                else -> hour
            }
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, adjustedHour)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
        }
        
        return System.currentTimeMillis() + 3600000 // 1 ساعت بعد
    }
}
