package com.persianai.assistant.ai

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import com.persianai.assistant.utils.*
import java.util.*

/**
 * دستیار صوتی فارسی پیشرفته با قابلیت‌های هوش مصنوعی
 */
class PersianVoiceAssistant(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    
    // مدیران مختلف
    private val smartReminderManager = SmartReminderManager(context)
    private val travelPlannerManager = TravelPlannerManager(context)
    private val bankingAssistantManager = BankingAssistantManager(context)
    private val carMaintenanceManager = CarMaintenanceManager(context)
    private val preferencesManager = PreferencesManager(context)
    
    // State flows برای وضعیت دستیار
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    
    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse
    
    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory
    
    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }
    
    @Serializable
    data class ConversationMessage(
        val id: String,
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val category: MessageCategory = MessageCategory.GENERAL
    )
    
    @Serializable
    enum class MessageCategory {
        GENERAL, // عمومی
        REMINDER, // یادآور
        TRAVEL, // سفر
        BANKING, // بانکی
        CAR, // خودرو
        WEATHER, // آب و هوا
        NAVIGATION, // ناوبری
        HEALTH, // سلامتی
        ENTERTAINMENT // سرگرمی
    }
    
    /**
     * پردازش ورودی کاربر (متنی یا صوتی)
     */
    suspend fun processUserInput(input: String, isVoice: Boolean = false): String {
        return try {
            _isListening.value = true
            
            // افزودن پیام کاربر به تاریخچه
            val userMessage = ConversationMessage(
                id = UUID.randomUUID().toString(),
                text = input,
                isUser = true,
                category = categorizeMessage(input)
            )
            addToHistory(userMessage)
            
            // پردازش و تولید پاسخ
            val response = generateResponse(input)
            
            // افزودن پاسخ به تاریخچه
            val assistantMessage = ConversationMessage(
                id = UUID.randomUUID().toString(),
                text = response,
                isUser = false,
                category = categorizeMessage(input)
            )
            addToHistory(assistantMessage)
            
            _currentResponse.value = response
            
            // اگر ورودی صوتی بود، پاسخ هم صوتی شود
            if (isVoice) {
                speakResponse(response)
            }
            
            response
            
        } catch (e: Exception) {
            Log.e("PersianVoiceAssistant", "❌ خطا در پردازش ورودی: ${e.message}")
            "متاسفم در پردازش درخواست شما مشکلی پیش آمد. لطفا دوباره تلاش کنید."
        } finally {
            _isListening.value = false
        }
    }
    
    /**
     * تولید پاسخ هوشمند
     */
    private suspend fun generateResponse(input: String): String {
        val normalizedInput = input.lowercase().trim()
        
        return when {
            // دستورات یادآور
            normalizedInput.contains("یادآور") || normalizedInput.contains("یادآوری") -> {
                handleReminderCommands(normalizedInput)
            }
            
            // دستورات سفر
            normalizedInput.contains("سفر") || normalizedInput.contains("مسافرت") -> {
                handleTravelCommands(normalizedInput)
            }
            
            // دستورات بانکی
            normalizedInput.contains("حساب") || normalizedInput.contains("پول") || normalizedInput.contains("هزینه") -> {
                handleBankingCommands(normalizedInput)
            }
            
            // دستورات خودرو
            normalizedInput.contains("ماشین") || normalizedInput.contains("خودرو") || normalizedInput.contains("ماشینم") -> {
                handleCarCommands(normalizedInput)
            }
            
            // دستورات آب و هوا
            normalizedInput.contains("آب و هوا") || normalizedInput.contains("هوا") || normalizedInput.contains("آبوهوا") -> {
                handleWeatherCommands(normalizedInput)
            }
            
            // دستورات ناوبری
            normalizedInput.contains("مسیر") || normalizedInput.contains("آدرس") || normalizedInput.contains("راهنمایی") -> {
                handleNavigationCommands(normalizedInput)
            }
            
            // دستورات سلامتی
            normalizedInput.contains("سلامتی") || normalizedInput.contains("ورزش") || normalizedInput.contains("سلام") -> {
                handleHealthCommands(normalizedInput)
            }
            
            // دستورات عمومی
            normalizedInput.contains("سلام") -> {
                "سلام! چطور می‌تونم کمکتون کنم؟ من می‌تونم یادآورها، سفرها، مسائل مالی و خودرویی شما رو مدیریت کنم."
            }
            
            normalizedInput.contains("خداحافظ") -> {
                "خداحافظ! در صورت نیاز من همیشه آماده کمک هستم."
            }
            
            normalizedInput.contains("چطوری") || normalizedInput.contains("حالت چطوره") -> {
                "من عالی هستم و آماده کمک به شما هستم! امروز چطور می‌تونم کمکتون کنم؟"
            }
            
            normalizedInput.contains("کاری میتونی بکنی") || normalizedInput.contains("قابلیت") -> {
                "من می‌تونم:\n" +
                "📅 یادآورهای هوشمند تنظیم کنم\n" +
                "✈️ سفرها رو برنامه‌ریزی کنم\n" +
                "💰 مسائل مالی شما رو مدیریت کنم\n" +
                "🚗 نگهداری خودرو رو پیگیری کنم\n" +
                "🌤️ آب و هوا رو اطلاع بدم\n" +
                "🗺️ مسیریابی کنم\n" +
                "🏃‍♂️ سلامتی و ورزش رو مدیریت کنم"
            }
            
            // درخواست‌های زمانی
            normalizedInput.contains("ساعت چند") -> {
                val currentTime = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date())
                "ساعت فعلی: $currentTime"
            }
            
            normalizedInput.contains("امروز چندمه") -> {
                val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date())
                val persianDate = convertToPersianDate(currentDate)
                "امروز: $persianDate"
            }
            
            // پاسخ پیش‌فرض
            else -> {
                generateContextualResponse(normalizedInput)
            }
        }
    }
    
    /**
     * مدیریت دستورات یادآور
     */
    private fun handleReminderCommands(input: String): String {
        return when {
            input.contains("بعد") || input.contains("ساعت دیگر") -> {
                "یادآور برای یک ساعت دیگر تنظیم شد."
            }
            
            input.contains("فردا") -> {
                "یادآور برای فردا همین ساعت تنظیم شد."
            }
            
            input.contains("هر روز") -> {
                "یادآور روزانه با موفقیت تنظیم شد."
            }
            
            input.contains("لیست") || input.contains("نمایش") -> {
                val reminders = smartReminderManager.getActiveReminders()
                if (reminders.isEmpty()) {
                    "شما هیچ یادآور فعالی ندارید."
                } else {
                    val reminderList = reminders.take(5).joinToString("\n") { reminder ->
                        "📌 ${reminder.title}: ${reminder.message}"
                    }
                    "یادآورهای فعال شما:\n$reminderList"
                }
            }
            
            else -> {
                "برای تنظیم یادآور، لطفا زمان و موضوع مورد نظر را مشخص کنید. مثلا: 'یادآور برای فردا ساعت ۱۰: جلسه مهم'"
            }
        }
    }
    
    /**
     * مدیریت دستورات سفر
     */
    private fun handleTravelCommands(input: String): String {
        return when {
            input.contains("جدید") || input.contains("برنامه") -> {
                val upcomingTrips = travelPlannerManager.getUpcomingTrips()
                if (upcomingTrips.isEmpty()) {
                    "شما هیچ سفر آینده‌ای ندارید. می‌خواهید سفر جدیدی برنامه‌ریزی کنید؟"
                } else {
                    val tripList = upcomingTrips.take(3).joinToString("\n") { trip ->
                        "✈️ ${trip.title} به ${trip.destination}"
                    }
                    "سفرهای پیش رو:\n$tripList"
                }
            }
            
            input.contains("مقصد") -> {
                val destinations = travelPlannerManager.getDestinations().take(5)
                val destinationList = destinations.joinToString("\n") { dest ->
                    "🏛️ ${dest.name} - ${dest.description}"
                }
                "مقاصد پیشنهادی:\n$destinationList"
            }
            
            else -> {
                "برای برنامه‌ریزی سفر، می‌توانید مقصد، تاریخ و بودجه خود را مشخص کنید."
            }
        }
    }
    
    /**
     * مدیریت دستورات بانکی
     */
    private fun handleBankingCommands(input: String): String {
        return when {
            input.contains("موجودی") || input.contains("حساب") -> {
                val summary = bankingAssistantManager.getFinancialSummary()
                "خلاصه مالی شما:\n" +
                "💰 کل درآمد: ${String.format("%,.0f", summary.totalIncome)} تومان\n" +
                "💸 کل هزینه: ${String.format("%,.0f", summary.totalExpenses)} تومان\n" +
                "💵 موجودی کل: ${String.format("%,.0f", summary.totalBalance)} تومان\n" +
                "📊 نرخ پس‌انداز: ${String.format("%.1f", summary.savingsRate)}%"
            }
            
            input.contains("قبض") || input.contains("پرداخت") -> {
                val unpaidBills = bankingAssistantManager.getUnpaidBills()
                if (unpaidBills.isEmpty()) {
                    "شما هیچ قبض پرداخت نشده‌ای ندارید."
                } else {
                    val billList = unpaidBills.take(3).joinToString("\n") { bill ->
                        "📄 ${bill.title}: ${String.format("%,.0f", bill.amount)} تومان - سررسید: ${bill.dueDate}"
                    }
                    "قبوض پرداخت نشده:\n$billList"
                }
            }
            
            input.contains("هزینه") -> {
                val analysis = bankingAssistantManager.getExpenseAnalysis()
                if (analysis.isEmpty()) {
                    "هزینه‌ای در ماه جاری ثبت نشده است."
                } else {
                    val expenseList = (analysis as Map<String, Double>).take(5).entries.joinToString("\n") { entry: Map.Entry<String, Double> ->
                        val (category, amount) = entry
                        "📊 ${getCategoryName(category)}: ${String.format("%,.0f", amount)} تومان"
                    }
                    "تحلیل هزینه‌های ماه جاری:\n$expenseList"
                }
            }
            
            else -> {
                "برای اطلاعات مالی، می‌توانید موجودی، قبوض یا هزینه‌ها را درخواست کنید."
            }
        }
    }
    
    /**
     * مدیریت دستورات خودرو
     */
    private fun handleCarCommands(input: String): String {
        return when {
            input.contains("سرویس") || input.contains("تعمیر") -> {
                val recommendations = carMaintenanceManager.getMaintenanceRecommendations("default")
                if (recommendations.isEmpty()) {
                    "خودروی شما در وضعیت خوبی قرار دارد."
                } else {
                    val recList = recommendations.joinToString("\n") { "🔧 $it" }
                    "توصیه‌های نگهداری:\n$recList"
                }
            }
            
            input.contains("یادآور") -> {
                val dueReminders = carMaintenanceManager.getDueReminders()
                if (dueReminders.isEmpty()) {
                    "یادآور سررسید شده‌ای برای خودروی شما وجود ندارد."
                } else {
                    val reminderList = dueReminders.take(3).joinToString("\n") { reminder ->
                        "⚠️ ${reminder.title}: ${reminder.description}"
                    }
                    "یادآورهای سررسید شده:\n$reminderList"
                }
            }
            
            input.contains("هزینه") -> {
                val costs = carMaintenanceManager.getMaintenanceCosts()
                "هزینه‌های نگهداری خودرو:\n" +
                "💵 کل هزینه: ${String.format("%,.0f", costs.totalCost)} تومان\n" +
                "📅 هزینه امسال: ${String.format("%,.0f", costs.thisYearCost)} تومان\n" +
                "📊 میانگین ماهانه: ${String.format("%,.0f", costs.averageMonthlyCost)} تومان"
            }
            
            else -> {
                "برای خودروی شما می‌توانم سرویس‌ها، یادآورها و هزینه‌ها را مدیریت کنم."
            }
        }
    }
    
    /**
     * مدیریت دستورات آب و هوا
     */
    private fun handleWeatherCommands(input: String): String {
        return when {
            input.contains("امروز") -> {
                "امروز هوای تهران آفتابی و با دمای ۲۵ درجه سانتی‌گراد است. فردا احتمال بارش باران وجود دارد."
            }
            
            input.contains("فردا") -> {
                "فردا هوای تهران نیمه‌ابری و با دمای ۲۲ درجه سانتی‌گراد پیش‌بینی می‌شود."
            }
            
            input.contains("هفته") -> {
                "هفته آینده هوای تهران در حالت پایدار قرار خواهد داشت و دما بین ۲۰ تا ۲۸ درجه نوسان خواهد داشت."
            }
            
            else -> {
                "برای اطلاعات آب و هوا، لطفا زمان مورد نظر را مشخص کنید (امروز، فردا، هفته)."
            }
        }
    }
    
    /**
     * مدیریت دستورات ناوبری
     */
    private fun handleNavigationCommands(input: String): String {
        return when {
            input.contains("مسیر") -> {
                "برای پیدا کردن مسیر، لطفا مبدأ و مقصد خود را مشخص کنید."
            }
            
            input.contains("موقعیت") || input.contains("کجام") -> {
                "شما در حال حاضر در تهران، خیابان ولیعصر قرار دارید."
            }
            
            input.contains("نزدیک") -> {
                "نزدیک‌ترین مکان‌های مورد علاقه شما:\n" +
                "⛽ پمپ بنزین: ۵۰۰ متر\n" +
                "🏥 بیمارستان: ۱.۲ کیلومتر\n" +
                "🏪 سوپرمارکت: ۳۰۰ متر"
            }
            
            else -> {
                "برای ناوبری، می‌توانید مسیر، موقعیت یا مکان‌های نزدیک را درخواست کنید."
            }
        }
    }
    
    /**
     * مدیریت دستورات سلامتی
     */
    private fun handleHealthCommands(input: String): String {
        return when {
            input.contains("ورزش") -> {
                "پیشنهاد ورزش امروز: ۳۰ دقیقه پیاده‌روی سریع یا ۲۰ دقیقه دویدن سبک."
            }
            
            input.contains("آب") -> {
                "توصیه می‌شود روزانه ۸ لیوان آب بنوشید. امروز تاکنون ${getWaterIntake()} لیوان آب نوشیده‌اید."
            }
            
            input.contains("خواب") -> {
                "برای سلامتی، ۷-۸ ساعت خواب در شب توصیه می‌شود. دیروز ${getSleepHours()} ساعت خواب داشته‌اید."
            }
            
            else -> {
                "برای سلامتی، ورزش، تغذیه و خواب مناسب را در اولویت قرار دهید."
            }
        }
    }
    
    /**
     * تولید پاسخ زمینه‌ای
     */
    private fun generateContextualResponse(input: String): String {
        // تحلیل زمینه بر اساس تاریخچه گفتگو
        val recentMessages = _conversationHistory.value.takeLast(3)
        
        return when {
            recentMessages.any { it.category == MessageCategory.TRAVEL } -> {
                "آیا مایلید اطلاعات بیشتری در مورد سفر خود دریافت کنید؟"
            }
            
            recentMessages.any { it.category == MessageCategory.BANKING } -> {
                "آیا می‌خواهید گزارش مالی دقیق‌تری دریافت کنید؟"
            }
            
            recentMessages.any { it.category == MessageCategory.CAR } -> {
                "آیا نیاز به کمک در مورد نگهداری خودرو دارید؟"
            }
            
            else -> {
                generateGeneralResponse(input)
            }
        }
    }
    
    /**
     * تولید پاسخ عمومی
     */
    private fun generateGeneralResponse(input: String): String {
        val responses = listOf(
            "جالب است! می‌توانید بیشتر توضیح دهید؟",
            "متوجه شدم. چطور می‌توانم کمکتون کنم؟",
            "این موضوع مهمی است. بیایید با هم بررسی کنیم.",
            "عالی! برای شروع چه کاری می‌خواهید انجام دهیم؟",
            "من اینجا هستم تا کمک کنم. لطفا سوال خود را بپرسید."
        )
        
        return responses.random()
    }
    
    /**
     * دسته‌بندی پیام
     */
    private fun categorizeMessage(input: String): MessageCategory {
        val normalizedInput = input.lowercase()
        
        return when {
            normalizedInput.contains("یادآور") -> MessageCategory.REMINDER
            normalizedInput.contains("سفر") || normalizedInput.contains("مسافرت") -> MessageCategory.TRAVEL
            normalizedInput.contains("حساب") || normalizedInput.contains("پول") -> MessageCategory.BANKING
            normalizedInput.contains("ماشین") || normalizedInput.contains("خودرو") -> MessageCategory.CAR
            normalizedInput.contains("آب و هوا") || normalizedInput.contains("هوا") -> MessageCategory.WEATHER
            normalizedInput.contains("مسیر") || normalizedInput.contains("آدرس") -> MessageCategory.NAVIGATION
            normalizedInput.contains("سلامتی") || normalizedInput.contains("ورزش") -> MessageCategory.HEALTH
            else -> MessageCategory.GENERAL
        }
    }
    
    /**
     * افزودن به تاریخچه گفتگو
     */
    private fun addToHistory(message: ConversationMessage) {
        val currentHistory = _conversationHistory.value.toMutableList()
        currentHistory.add(message)
        
        // محدود کردن اندازه تاریخچه
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.removeAt(0)
        }
        
        _conversationHistory.value = currentHistory
    }
    
    /**
     * تبدیل پاسخ به گفتار
     */
    private suspend fun speakResponse(text: String) {
        try {
            // استفاده از TTS برای تبدیل متن به گفتار
            val persianTTS = PersianTTS(context)
            persianTTS.speak(text)
        } catch (e: Exception) {
            Log.e("PersianVoiceAssistant", "❌ خطا در تبدیل متن به گفتار: ${e.message}")
        }
    }
    
    /**
     * تبدیل تاریخ به شمسی
     */
    private fun convertToPersianDate(date: String): String {
        // پیاده‌سازی تبدیل تاریخ میلادی به شمسی
        return "۱۴۰۲/۰۸/۲۴" // نمونه
    }
    
    /**
     * دریافت میزان آب مصرفی
     */
    private fun getWaterIntake(): Int {
        return preferencesManager.getInt("water_intake", 3)
    }
    
    /**
     * دریافت ساعت خواب
     */
    private fun getSleepHours(): Double {
        return preferencesManager.getDouble("sleep_hours", 6.5)
    }
    
    /**
     * دریافت نام دسته‌بندی
     */
    private fun getCategoryName(category: String): String {
        return when (category) {
            "FOOD" -> "خوراک"
            "TRANSPORT" -> "حمل و نقل"
            "SHOPPING" -> "خرید"
            "ENTERTAINMENT" -> "سرگرمی"
            "HEALTH" -> "سلامتی"
            "EDUCATION" -> "آموزشی"
            "BILLS" -> "قبوض"
            "SALARY" -> "حقوق"
            "INVESTMENT" -> "سرمایه‌گذاری"
            "OTHER" -> "سایر"
            else -> category
        }
    }
    
    /**
     * شروع گوش دادن به ورودی صوتی
     */
    fun startListening() {
        _isListening.value = true
        // پیاده‌سازی تشخیص گفتار
        Log.i("PersianVoiceAssistant", "🎤 شروع گوش دادن...")
    }
    
    /**
     * توقف گوش دادن
     */
    fun stopListening() {
        _isListening.value = false
        Log.i("PersianVoiceAssistant", "🛑 توقف گوش دادن")
    }
    
    /**
     * پاک‌سازی تاریخچه گفتگو
     */
    fun clearHistory() {
        _conversationHistory.value = emptyList()
        Log.i("PersianVoiceAssistant", "🧹 تاریخچه گفتگو پاک‌سازی شد")
    }
    
    /**
     * دریافت خلاصه گفتگو
     */
    fun getConversationSummary(): String {
        val history = _conversationHistory.value
        val userMessages = history.count { it.isUser }
        val assistantMessages = history.count { !it.isUser }
        val categories = history.groupBy { it.category }.mapValues { it.value.size }
        
        return "خلاصه گفتگو:\n" +
               "📝 پیام‌های کاربر: $userMessages\n" +
               "🤖 پاسخ‌های دستیار: $assistantMessages\n" +
               "📊 موضوعات: ${categories.entries.joinToString { "${it.key}: ${it.value}" }}"
    }
    
    /**
     * پاک‌سازی منابع
     */
    fun cleanup() {
        scope.cancel()
        Log.i("PersianVoiceAssistant", "🧹 منابع PersianVoiceAssistant پاک‌سازی شد")
    }
}
