package com.persianai.assistant.ai

import android.content.Context
import android.util.Log
import kotlin.math.pow

/**
 * SimpleOfflineResponder: پاسخ‌دهنده آفلاین بدون نیاز به Native Library
 * 
 * یک سیستم Dictionary-based و Intent-based است که:
 * - بدون llama.cpp کار می‌کند
 * - سوالات فارسی رایج را پاسخ می‌دهد
 * - Intent parsing برای دسته‌بندی سوالات
 */
object SimpleOfflineResponder {
    
    private const val TAG = "SimpleOfflineResponder"
    
    /**
     * تقاضای پاسخ برای متن ورودی
     * @param text سوال یا درخواست کاربر
     * @return پاسخ مناسب یا null اگر نتوانست پاسخ دهد
     */
    fun respond(context: Context, text: String): String? {
        return try {
            val intent = parseIntent(text)
            Log.d(TAG, "Detected intent: ${intent.first}")
            
            val response = when (intent.first) {
                "GREETING" -> handleGreeting(intent.second)
                "TIME_DATE" -> handleTimeDate(context, intent.second)
                "WEATHER" -> handleWeather(intent.second)
                "MATH" -> handleMath(intent.second)
                "GENERAL_QA" -> handleGeneralQA(intent.second)
                "HELP" -> handleHelp()
                "CALCULATION" -> handleCalculation(intent.second)
                "DEFINITION" -> handleDefinition(intent.second)
                "NAVIGATION" -> handleNavigation(intent.second)
                "REMINDER" -> handleReminder(intent.second)
                "OFFLINE_STATUS" -> handleOfflineStatus()
                else -> null
            }
            
            response?.let { Log.d(TAG, "Response length: ${it.length}") }
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error in respond", e)
            null
        }
    }
    
    /**
     * تجزیه Intent از متن
     * @return Pair<Intent, Keywords>
     */
    private fun parseIntent(text: String): Pair<String, List<String>> {
        val normalizedText = text.lowercase().trim()
        val keywords = normalizedText.split(Regex("[\\s،\\.\\!\\?]+"))
        
        // بررسی Greeting‌ها
        if (anyMatch(normalizedText, listOf("سلام", "درود", "خسته نباشی", "صبح بخیر", "شب بخیر", "ببخشید"))) {
            return Pair("GREETING", keywords)
        }

        // بررسی یادآورها (اولویت بالاتر از زمان/تاریخ تا "هر روز" به اشتباه TIME_DATE نشود)
        if (anyMatch(normalizedText, listOf("یادآور", "یادم", "یادم بنداز", "یادآوری", "بیدارباش", "آلارم", "هشدار", "هر روز", "روزانه", "فراموش"))) {
            return Pair("REMINDER", keywords)
        }
        
        // بررسی سوالات زمان و تاریخ
        if (anyMatch(normalizedText, listOf("ساعت", "وقت", "تاریخ", "امروز", "فردا", "دیروز", "سال", "ماه", "روز"))) {
            return Pair("TIME_DATE", keywords)
        }
        
        // بررسی سوالات آب و هوا
        if (anyMatch(normalizedText, listOf("هوا", "آب", "بارش", "دما", "باد", "سرما", "گرمی"))) {
            return Pair("WEATHER", keywords)
        }
        
        // بررسی محاسبات ریاضی
        if (Regex("[\\d\\+\\-\\*\\/\\(\\)\\^]+").containsMatchIn(normalizedText)) {
            return Pair("CALCULATION", keywords)
        }
        
        // بررسی سوالات تعاریف
        if (anyMatch(normalizedText, listOf("معنی", "تعریف", "چیست", "یعنی", "مقصود"))) {
            return Pair("DEFINITION", keywords)
        }
        
        // بررسی سوالات مسیریابی
        if (anyMatch(normalizedText, listOf("راه", "مسیر", "رفتن", "جهت", "نقشه", "جا", "پیدا"))) {
            return Pair("NAVIGATION", keywords)
        }
        
        // بررسی کمک
        if (anyMatch(normalizedText, listOf("کمک", "راهنمایی", "چطور", "چگونه", "توضیح"))) {
            return Pair("HELP", keywords)
        }
        
        // بررسی وضعیت آفلاین
        if (anyMatch(normalizedText, listOf("آفلاین", "اینترنت", "ارتباط", "اتصال", "وضعیت"))) {
            return Pair("OFFLINE_STATUS", keywords)
        }
        
        // پاسخ عمومی
        return Pair("GENERAL_QA", keywords)
    }
    
    private fun handleGreeting(keywords: List<String>): String {
        val greetings = listOf(
            "👋 سلام! چطور می‌تونم کمکت کنم؟",
            "درود! خوشحالم کمک کنم 😊",
            "سلام علیکم! بگو نیاز چی داری",
            "هی! خوش اومدی! چی می‌گذره؟"
        )
        return greetings.random()
    }
    
    private fun handleTimeDate(context: Context, keywords: List<String>): String {
        val cal = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale("fa", "IR"))
        val dateFormat = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale("fa", "IR"))
        
        return """
        ⏰ **وضعیت زمان‌شناسی:**
        
        ⌚ ساعت: ${sdf.format(cal.time)}
        📅 تاریخ امروز: ${dateFormat.format(cal.time)}
        """.trimIndent()
    }
    
    private fun handleWeather(keywords: List<String>): String {
        return """
        🌤️ **اطلاعات آب و هوا:**
        
        برای دریافت اطلاعات دقیق آب و هوا:
        1️⃣ به تنظیمات برو (⚙️)
        2️⃣ یک کلید API از OpenWeatherAPI یا AQI تهیه کن
        3️⃣ سپس دوباره سوال بپرس
        
        ⚡ نکته: آفلاین می‌تونم اطلاعات ذخیره‌شده برای شهر‌های اصلی فراهم کنم
        """.trimIndent()
    }
    
    private fun handleMath(keywords: List<String>): String {
        return """
        🧮 **ریاضیات و محاسبات:**
        
        می‌تونم محاسبات ریاضی انجام بدم! 
        مثال‌ها:
        • 12 + 5 = 17
        • 100 - 30 = 70
        • 6 × 7 = 42
        • 100 ÷ 5 = 20
        
        بگو معادله یا محاسبه‌ات رو 😊
        """.trimIndent()
    }
    
    private fun handleCalculation(keywords: List<String>): String {
        // تلاش برای محاسبه ساده
        val text = keywords.joinToString(" ")
        return try {
            // محاسبات بسیار ساده
            when {
                text.contains("+") -> {
                    val parts = text.split("+").map { it.trim().toDoubleOrNull() }
                    if (parts.all { it != null }) {
                        val result = parts.filterNotNull().sum()
                        "✅ نتیجه: $result"
                    } else {
                        "❌ نتونستم محاسبه کنم، لطفاً شماره‌ها واضح‌تر بگو"
                    }
                }
                text.contains("-") -> {
                    val parts = text.split("-").map { it.trim().toDoubleOrNull() }
                    if (parts.size == 2 && parts.all { it != null }) {
                        val result = parts[0]!! - parts[1]!!
                        "✅ نتیجه: $result"
                    } else {
                        "❌ نتونستم محاسبه کنم"
                    }
                }
                text.contains("*") || text.contains("×") -> {
                    val num1 = Regex("(\\d+)").find(text)?.value?.toDoubleOrNull()
                    val num2 = Regex("\\*(\\d+)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
                    if (num1 != null && num2 != null) {
                        val result = num1 * num2
                        "✅ نتیجه: $result"
                    } else {
                        "❌ نتونستم محاسبه کنم"
                    }
                }
                else -> "🧮 این نوع محاسبه رو کامل متوجه نشدم؛ لطفاً به شکل «عدد + عدد» یا «عدد - عدد» یا «عدد × عدد» بنویس."
            }
        } catch (e: Exception) {
            "❌ خطا در محاسبه: ${e.message}"
        }
    }
    
    private fun handleDefinition(keywords: List<String>): String {
        val word = keywords.firstOrNull() ?: return "❌ کلمه‌ای برای تعریف پیدا نکردم"
        
        val definitions = mapOf(
            "دستیار" to "برنامه‌ای که کمک می‌کند و سوالات رو پاسخ می‌ده",
            "آفلاین" to "کار کردن بدون نیاز به اتصال اینترنت",
            "آنلاین" to "کار کردن با اتصال اینترنت",
            "api" to "API یا Application Programming Interface، ابزار برای ارتباط برنامه‌ها"
        )
        
        val foundDefinition = definitions[word] ?: return "❌ تعریف برای '$word' پیدا نکردم"
        return "📖 **تعریف:**\n\n$word: $foundDefinition"
    }
    
    private fun handleNavigation(keywords: List<String>): String {
        return """
        🗺️ **مسیریابی و نقشه:**
        
        برای استفاده از مسیریابی:
        1️⃣ تنظیمات 👈 API Keys
        2️⃣ Neshan API یا OpenStreetMap
        3️⃣ سپس مسیری درخواست کن
        
        📍 مثال: "مسیر تا خیابان فردوسی"
        """.trimIndent()
    }
    
    private fun handleReminder(keywords: List<String>): String {
        return """
        🔔 **یادآورها:**
        
        می‌تونی یادآوری‌های خود رو:
        ✅ تنظیم کنی
        ✅ مشاهده کنی
        ✅ ویرایش کنی
        ✅ حذف کنی
        
        مثال: "یادآور کن فردا ساعت 8 صبح جلسه"
        """.trimIndent()
    }
    
    private fun handleGeneralQA(keywords: List<String>): String {
        return """
        📚 **پاسخ عمومی:**
        
        من یک دستیار آفلاین هستم که:
        ✨ سوالات فارسی پاسخ می‌دم
        ✨ کمک می‌کنم تو استفاده از برنامه
        ✨ اطلاعات کلی فراهم می‌کنم
        
        سوال‌ات رایج:
        • "ساعت چند شد؟"
        • "امروز چه روزیه؟"
        • "آب و هوا چطوره؟"
        • "راهنمایی بده"
        """.trimIndent()
    }
    
    private fun handleHelp(): String {
        return """
        ℹ️ **راهنمایی برنامه:**
        
        **📱 بخش‌های اصلی:**
        1️⃣ **چت هوشمند** - صحبت با AI
        2️⃣ **تقویم** - مناسبت‌های فارسی
        3️⃣ **مسیریابی** - یافتن راه
        4️⃣ **یادآورها** - فعالیت‌های برنامه‌ریزی‌شده
        5️⃣ **هزینه‌های مالی** - ثبت درآمد و هزینه
        6️⃣ **موسیقی** - پخش و لیست‌های موسیقی
        
        **⚙️ تنظیمات:**
        • کلیدهای API
        • مدل‌های آفلاین
        • ترجیحات شخصی
        
        **💡 نکات:**
        • برای AI بهتر، از API فعال کن
        • آفلاین می‌تونی از بسیاری قابلیت‌ها استفاده کنی
        • داده‌هات تماماً محلی و ایمن است
        """.trimIndent()
    }
    
    private fun handleOfflineStatus(): String {
        return """
        📡 **وضعیت اتصال:**
        
        ✅ **آفلاین مد فعال**
        
        • برنامه بدون اتصال اینترنت کار می‌کند
        • داده‌های محلی استفاده می‌شوند
        • پاسخ‌ها سریع‌تر هستند
        
        🌐 **برای آنلاین شدن:**
        1️⃣ تنظیمات ⚙️
        2️⃣ کلیدهای API
        3️⃣ OpenAI / OpenRouter / AIML API
        
        ⚡ **حتی آفلاین می‌تونی:**
        • چت کن
        • تقویم ببین
        • یادآورها بخور
        • موسیقی بشنو
        • هزینه‌ها ثبت کن
        """.trimIndent()
    }
    
    /**
     * بررسی اینکه آیا هر یک از کلمات کلیدی در متن موجود است
     */
    private fun anyMatch(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
}
