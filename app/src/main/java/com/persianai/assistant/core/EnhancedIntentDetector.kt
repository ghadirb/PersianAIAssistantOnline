package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.core.intent.*

/**
 * تشخیص‌دهنده Intent پیشرفته‌تر با الگوهای Persian
 */
class EnhancedIntentDetector(private val context: Context) {
    
    private val tag = "EnhancedIntentDetector"
    
    fun detectIntent(text: String): AIIntent {
        val t = text.trim().lowercase()
        
        return when {
            // Reminder Patterns
            matchesReminder(t) -> {
                val type = extractReminderType(text)
                ReminderCreateIntent(rawText = text, type = type)
            }
            matchesReminderList(t) -> ReminderListIntent(rawText = text)
            matchesReminderDelete(t) -> ReminderDeleteIntent(rawText = text)
            
            // Navigation Patterns
            matchesNavigation(t) -> {
                val dest = extractDestination(text)
                if (matchesNavigationStart(t)) {
                    NavigationStartIntent(rawText = text, destination = dest)
                } else {
                    NavigationSearchIntent(rawText = text, destination = dest)
                }
            }
            
            // Finance Patterns
            matchesFinance(t) -> {
                if (matchesFinanceReport(t)) {
                    FinanceReportIntent(rawText = text, timeRange = extractTimeRange(text))
                } else {
                    FinanceTrackIntent(rawText = text, type = extractFinanceType(text))
                }
            }
            
            // Education Patterns
            matchesEducation(t) -> {
                if (matchesGenerateQuestion(t)) {
                    val level = extractEducationLevel(text)
                    EducationGenerateQuestionIntent(rawText = text, level = level)
                } else {
                    val topic = extractEducationTopic(text)
                    EducationAskIntent(rawText = text, topic = topic)
                }
            }
            
            // Call Patterns
            matchesCall(t) -> {
                val name = extractContactName(text)
                CallSmartIntent(rawText = text, contactName = name)
            }
            
            // Weather Patterns
            matchesWeather(t) -> {
                val location = extractLocation(text)
                WeatherCheckIntent(rawText = text, location = location)
            }
            
            // Music Patterns
            matchesMusic(t) -> {
                val query = extractMusicQuery(text)
                MusicPlayIntent(rawText = text, query = query)
            }
            
            else -> AssistantChatIntent(rawText = text)
        }
    }
    
    // ===== Reminder =====
    private fun matchesReminder(t: String) = 
        t.contains(Regex("یاد(آ|هم)|(بن)داز|یادبده|یادآوری|یادم بنداز")) ||
        t.contains("یادآور") || t.contains("یادم بندازید")
    
    private fun matchesReminderList(t: String) =
        (t.contains("لیست") || t.contains("فهرست") || t.contains("بده")) && t.contains("یادآوری")
    
    private fun matchesReminderDelete(t: String) =
        (t.contains("حذف") || t.contains("حذف کن")) && t.contains("یادآوری")
    
    private fun extractReminderType(text: String): String? {
        return when {
            text.contains(Regex("آلارم|بیدار")) -> "alarm"
            text.contains("روزانه") -> "daily"
            else -> "reminder"
        }
    }
    
    // ===== Navigation =====
    private fun matchesNavigation(t: String) =
        t.contains("مسیریابی") || t.contains("مسیر") || 
        t.contains("نقشه") || t.contains("navigation") ||
        t.contains("برو") || t.contains("دستورالعمل")
    
    private fun matchesNavigationStart(t: String) =
        t.contains(Regex("شروع|برو|برای|بروم|رفتن"))
    
    private fun extractDestination(text: String): String? {
        val patterns = listOf(
            "(?:به|برای|تا|سمت)\\s+([\\p{L}\\s]+?)(?:\\s+(?:برو|مسیریابی|نقشه)|$)",
            "(?:مسیریابی|نقشه).*?(?:به|برای)\\s+([\\p{L}\\s]+?)$"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val result = regex.find(text)
            if (result != null) {
                return result.groupValues.getOrNull(1)?.trim()
            }
        }
        return null
    }
    
    // ===== Finance =====
    private fun matchesFinance(t: String) =
        t.contains("درآمد") || t.contains("هزینه") || t.contains("خرج") ||
        t.contains("قسط") || t.contains("چک") || t.contains("اقساط") ||
        t.contains("مالی") || t.contains("تراکنش")
    
    private fun matchesFinanceReport(t: String) =
        t.contains("گزارش") || t.contains("خلاصه") || t.contains("آمار")
    
    private fun extractFinanceType(text: String): String? {
        return when {
            text.contains("درآمد") -> "income"
            text.contains(Regex("هزینه|خرج")) -> "expense"
            text.contains("قسط") -> "installment"
            text.contains("چک") -> "check"
            else -> "all"
        }
    }
    
    private fun extractTimeRange(text: String): String? {
        return when {
            text.contains(Regex("امروز|اینروز")) -> "today"
            text.contains(Regex("این\\s+هفته|هفتگی")) -> "week"
            text.contains(Regex("این\\s+ماه|ماهانه")) -> "month"
            text.contains(Regex("سال|سالانه")) -> "year"
            else -> "month"
        }
    }
    
    // ===== Education =====
    private fun matchesEducation(t: String) =
        t.contains("درس") || t.contains("آموزش") || t.contains("بپرس") ||
        t.contains("سوال") || t.contains("شرح") || t.contains("تشریح") ||
        t.contains("توضیح") || t.contains("معلم")
    
    private fun matchesGenerateQuestion(t: String) =
        t.contains(Regex("سوال.*ساز|سوال.*بساز|سوال.*ایجاد")) || 
        t.contains("تمرین")
    
    private fun extractEducationLevel(text: String): String? {
        return when {
            text.contains(Regex("ابتدایی|ساده|آسان")) -> "basic"
            text.contains(Regex("متوسط|میانی")) -> "intermediate"
            text.contains(Regex("پیشرفته|دشوار|سخت")) -> "advanced"
            else -> "intermediate"
        }
    }
    
    private fun extractEducationTopic(text: String): String? {
        val withoutKeywords = text
            .replace(Regex("درس|آموزش|بپرس|شرح|توضیح"), "")
            .trim()
        return if (withoutKeywords.isNotBlank()) withoutKeywords else null
    }
    
    // ===== Call =====
    private fun matchesCall(t: String) =
        t.contains("تماس") || t.contains("کال") || t.contains("صدا")
    
    private fun extractContactName(text: String): String? {
        val patterns = listOf(
            "تماس\\s+(?:با|برای)\\s+([\\p{L}\\s]+?)(?:\\s+را)?$",
            "(?:کال|صدا\\s+زدن)\\s+([\\p{L}\\s]+?)$"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val result = regex.find(text)
            if (result != null) {
                return result.groupValues.getOrNull(1)?.trim()
            }
        }
        return null
    }
    
    // ===== Weather =====
    private fun matchesWeather(t: String) =
        t.contains("آب‌وهوا") || t.contains("هوا") || 
        t.contains("بارش") || t.contains("دما") || t.contains("آفتاب")
    
    private fun extractLocation(text: String): String? {
        val patterns = listOf(
            "(?:در|ب)\\s+([\\p{L}\\s]+?)$",
            "آب‌وهوا.*?(?:در|ب)\\s+([\\p{L}\\s]+?)$"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val result = regex.find(text)
            if (result != null) {
                return result.groupValues.getOrNull(1)?.trim()
            }
        }
        return null
    }
    
    // ===== Music =====
    private fun matchesMusic(t: String) =
        t.contains("موسیقی") || t.contains("آهنگ") || 
        t.contains("ترانه") || t.contains("بازی") || t.contains("پخش")
    
    private fun extractMusicQuery(text: String): String? {
        val keywords = listOf("موسیقی", "آهنگ", "ترانه", "بازی", "پخش")
        var result = text
        
        for (keyword in keywords) {
            result = result.replace(Regex(keyword, RegexOption.IGNORE_CASE), "")
        }
        
        return result.trim().takeIf { it.isNotBlank() } ?: text
    }
}