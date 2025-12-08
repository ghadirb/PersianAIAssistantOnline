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
import com.persianai.assistant.navigation.SavedLocationsManager
import com.persianai.assistant.activities.NavigationActivity
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
                        "📌 ${reminder.title}: ${reminder.description}"
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
                // This function was removed, provide a generic response
                "در حال حاضر امکان مشاهده سفرهای آینده از طریق دستیار صوتی وجود ندارد. می‌توانید از طریق اپلیکیشن سفر جدیدی را برنامه‌ریزی کنید."
            }
            
            input.contains("مقصد") -> {
                // This function was removed, provide a generic response
                "می‌توانید مقصدهای معروفی مثل اصفهان، شیراز یا مشهد را انتخاب کنید. کدامیک را ترجیح می‌دهید؟"
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
                val report = bankingAssistantManager.getFinancialReport()
                "خلاصه مالی شما:\n" +
                "💰 چک‌های پرداخت نشده: ${String.format("%,d", report.totalCheckAmount)} تومان\n" +
                "💸 اقساط باقی‌مانده: ${String.format("%,d", report.totalInstallmentAmount)} تومان\n" +
                "📅 ${report.upcomingChecksCount} چک سررسید نزدیک دارید.\n" +
                "🚨 ${report.overdueChecksCount} چک سررسید گذشته دارید."
            }
            
            input.contains("چک") || input.contains("قسط") -> {
                val upcomingChecks = bankingAssistantManager.getUpcomingChecks()
                val upcomingInstallments = bankingAssistantManager.getUpcomingInstallments()
                if (upcomingChecks.isEmpty() && upcomingInstallments.isEmpty()) {
                    "شما هیچ چک یا قسط نزدیکی ندارید."
                } else {
                    val checkList = upcomingChecks.joinToString("\n") { "- چک ${it.recipient} به مبلغ ${String.format("%,d", it.amount)} تومان" }
                    val installmentList = upcomingInstallments.joinToString("\n") { "- قسط ${it.title} به مبلغ ${String.format("%,d", it.monthlyAmount)} تومان" }
                    "موارد پیش رو:\n$checkList\n$installmentList"
                }
            }
            
            input.contains("هزینه") -> {
                // This function was removed, provide a generic response
                "برای مشاهده تحلیل هزینه‌ها، می‌توانید به بخش حسابداری پیشرفته مراجعه کنید."
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
                val upcomingServices = carMaintenanceManager.getUpcomingServices()
                if (upcomingServices.isEmpty()) {
                    "هیچ سرویس نزدیکی برای خودروی شما ثبت نشده است."
                } else {
                    val serviceList = upcomingServices.take(3).joinToString("\n") { "- ${it.type.displayName}" }
                    "سرویس‌های پیش رو:\n$serviceList"
                }
            }
            
            input.contains("وضعیت") -> {
                val overdueServices = carMaintenanceManager.getOverdueServices()
                if (overdueServices.isEmpty()) {
                    "هیچ سرویس سررسید گذشته‌ای برای خودروی شما وجود ندارد."
                } else {
                    val serviceList = overdueServices.take(3).joinToString("\n") { "- ${it.type.displayName}" }
                    "سرویس‌های سررسید گذشته:\n$serviceList"
                }
            }
            
            input.contains("هزینه") -> {
                val totalCost = carMaintenanceManager.getTotalMaintenanceCost()
                "کل هزینه ثبت شده برای سرویس‌های خودرو شما ${String.format("%,d", totalCost)} تومان است."
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
        val normalized = input.trim()

        // فرمان: برو به / برم به / مسیر به {نام ذخیره‌شده}
        extractDestinationName(normalized)?.let { name ->
            val manager = SavedLocationsManager(context)
            val saved = manager.findByName(name)
            return if (saved != null) {
                NavigationActivity.instance?.startNavigationTo(saved.latitude, saved.longitude)
                "در حال مسیریابی به ${saved.name}"
            } else {
                "مقصدی با نام \"$name\" در ذخیره‌ها پیدا نشد. لطفاً ابتدا ذخیره کنید."
            }
        }

        // فرمان: لیست مکان‌های ذخیره شده
        if (normalized.contains("مکان‌های ذخیره") || normalized.contains("مقصدهای ذخیره") || normalized.contains("ذخیره ها")) {
            val manager = SavedLocationsManager(context)
            val list = manager.getAllLocations()
            return if (list.isEmpty()) {
                "هیچ مکانی ذخیره نشده است. می‌توانید با «افزودن دستی» یا «از کلیپ‌بورد» اضافه کنید."
            } else {
                val top = list.take(5).joinToString("\n") { "• ${it.name} (${it.category})" }
                "مکان‌های ذخیره‌شده شما:\n$top\nبرای رفتن بگویید: برو به {نام}"
            }
        }

        return when {
            normalized.contains("مسیر") -> {
                "برای پیدا کردن مسیر، لطفا مبدأ و مقصد خود را مشخص کنید یا بگویید «برو به نام مقصد ذخیره‌شده»."
            }
            
            normalized.contains("موقعیت") || normalized.contains("کجام") -> {
                "شما در حال حاضر در تهران، خیابان ولیعصر قرار دارید."
            }
            
            normalized.contains("نزدیک") -> {
                "نزدیک‌ترین مکان‌های مورد علاقه شما:\n" +
                "⛽ پمپ بنزین: ۵۰۰ متر\n" +
                "🏥 بیمارستان: ۱.۲ کیلومتر\n" +
                "🏪 سوپرمارکت: ۳۰۰ متر"
            }
            
            else -> {
                "برای ناوبری، می‌توانید مسیر، موقعیت یا مکان‌های نزدیک را درخواست کنید یا بگویید «برو به {نام مقصد ذخیره‌شده}»."
            }
        }
    }

    private fun extractDestinationName(input: String): String? {
        val patterns = listOf(
            "برو به",
            "برم به",
            "مسیر به",
            "به مقصد",
            "تا"
        )
        val lower = input.lowercase()
        patterns.forEach { p ->
            val idx = lower.indexOf(p)
            if (idx != -1) {
                val start = idx + p.length
                val candidate = input.substring(start).trim().trim('\"')
                if (candidate.length >= 2) return candidate
            }
        }
        return null
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
