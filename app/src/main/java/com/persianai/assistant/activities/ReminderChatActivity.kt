package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.SmartReminderManager
import com.persianai.assistant.utils.SmartReminderManager.RepeatPattern
import com.persianai.assistant.utils.SmartReminderManager.ReminderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class ReminderChatActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatBinding = ActivityChatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)

        setSupportActionBar(chatBinding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "چت با دستیار یادآوری"

        setupChatUI()

        addMessage(
            com.persianai.assistant.models.ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "سلام! برای تنظیم یادآوری، فقط کافیه بگی. مثلا: «فردا ساعت ۱۰ صبح یادم بنداز جلسه دارم»",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView = chatBinding.recyclerView
    override fun getMessageInput(): com.google.android.material.textfield.TextInputEditText = chatBinding.messageInput
    override fun getSendButton(): View = chatBinding.sendButton
    override fun getVoiceButton(): View = chatBinding.voiceButton

    override suspend fun handleRequest(text: String): String {
        return withContext(Dispatchers.Default) {
            val parsed = parseReminderInput(text)
            if (parsed == null) {
                return@withContext "⚠️ متوجه نشدم. نمونه بگو: «فردا ساعت ۹ یادم بنداز قبض پرداخت کنم» یا «جمعه‌ها ۷ صبح بیدارم کن» یا «یک ساعت قبل پرواز یادآوری کن»."
            }

            val mgr = SmartReminderManager(this@ReminderChatActivity)
            val triggerMillis = parsed.triggerTime

            if (parsed.repeat == RepeatPattern.ONCE) {
                mgr.createSimpleReminder(
                    title = parsed.message,
                    description = "",
                    triggerTime = triggerMillis,
                    priority = parsed.priority
                )
            } else {
                mgr.createRecurringReminder(
                    title = parsed.message,
                    description = "",
                    firstTriggerTime = triggerMillis,
                    repeatPattern = parsed.repeat,
                    customDays = parsed.customDays,
                    priority = parsed.priority
                )
            }

            val repeatText = when (parsed.repeat) {
                RepeatPattern.DAILY -> "هر روز"
                RepeatPattern.WEEKLY -> "هفتگی"
                RepeatPattern.MONTHLY -> "ماهانه"
                RepeatPattern.YEARLY -> "سالیانه"
                RepeatPattern.WEEKDAYS -> "روزهای کاری"
                RepeatPattern.WEEKENDS -> "آخر هفته"
                RepeatPattern.CUSTOM -> "روزهای انتخابی"
                else -> "یک‌بار"
            }
            val timeText = android.text.format.DateFormat.format("HH:mm", triggerMillis)
            val dateText = android.text.format.DateFormat.format("yyyy/MM/dd", triggerMillis)
            return@withContext "✅ یادآوری «${parsed.message}» برای $dateText ساعت $timeText (${repeatText}) تنظیم شد."
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ==================== NLP ساده فارسی ====================
    private data class ParsedReminder(
        val message: String,
        val triggerTime: Long,
        val repeat: RepeatPattern,
        val customDays: List<Int> = emptyList(),
        val priority: SmartReminderManager.Priority = SmartReminderManager.Priority.MEDIUM
    )

    private val dayMap = mapOf(
        "شنبه" to Calendar.SATURDAY,
        "یکشنبه" to Calendar.SUNDAY,
        "دوشنبه" to Calendar.MONDAY,
        "سه شنبه" to Calendar.TUESDAY,
        "سه‌شنبه" to Calendar.TUESDAY,
        "چهارشنبه" to Calendar.WEDNESDAY,
        "پنجشنبه" to Calendar.THURSDAY,
        "جمعه" to Calendar.FRIDAY
    )

    private fun parseReminderInput(text: String): ParsedReminder? {
        val lower = text.replace("‌", " ").lowercase(Locale.getDefault())

        val message = extractMessage(lower) ?: return null

        val nowCal = Calendar.getInstance()
        var cal = Calendar.getInstance()

        // زمان: HH:mm یا "ساعت ۷" یا "7 صبح"
        val time = extractTime(lower)
        time?.let {
            cal.set(Calendar.HOUR_OF_DAY, it.first)
            cal.set(Calendar.MINUTE, it.second)
            cal.set(Calendar.SECOND, 0)
        } ?: run {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }

        // تاریخ: امروز/فردا/پس‌فردا یا تاریخ شمسی 1403/10/12
        when {
            lower.contains("پس فردا") || lower.contains("پسفردا") -> cal.add(Calendar.DAY_OF_MONTH, 2)
            lower.contains("فردا") -> cal.add(Calendar.DAY_OF_MONTH, 1)
            else -> {
                val jalali = extractJalaliDate(lower)
                if (jalali != null) {
                    val (gy, gm, gd) = PersianDateConverter.persianToGregorian(jalali.first, jalali.second, jalali.third)
                    cal.set(gy, gm - 1, gd)
                }
            }
        }

        // اگر زمان گذشته، یک روز بعد
        if (cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // تکرار
        val (repeatPattern, customDays) = extractRepeat(lower)

        // اولویت
        val priority = when {
            lower.contains("فوری") || lower.contains("اضطراری") -> SmartReminderManager.Priority.URGENT
            lower.contains("مهم") -> SmartReminderManager.Priority.HIGH
            else -> SmartReminderManager.Priority.MEDIUM
        }

        // الگوهای نسبی: "یک ساعت قبل" یا "نیم ساعت قبل"
        val offsetMinutes = extractOffsetMinutes(lower)
        if (offsetMinutes != null) {
            cal.add(Calendar.MINUTE, -offsetMinutes)
            if (cal.timeInMillis < System.currentTimeMillis()) {
                cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, offsetMinutes) // برگردان اگر منفی شد
            }
        }

        return ParsedReminder(
            message = message,
            triggerTime = cal.timeInMillis,
            repeat = repeatPattern,
            customDays = customDays,
            priority = priority
        )
    }

    private fun extractMessage(text: String): String? {
        // حذف عبارات زمان/تکرار برای پیام کوتاه
        var msg = text
        listOf("فردا", "پس فردا", "پسفردا", "هر روز", "روزانه", "هفتگی", "ماهانه").forEach {
            msg = msg.replace(it, "")
        }
        dayMap.keys.forEach { msg = msg.replace(it, "") }
        msg = msg.replace(Regex("\\d{1,2}:\\d{1,2}"), "")
        msg = msg.replace("ساعت", "")
        msg = msg.replace("یک ساعت قبل", "")
        msg = msg.replace("نیم ساعت قبل", "")
        msg = msg.replace("قبل از", "")
        msg = msg.trim()
        return if (msg.isBlank()) null else msg
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        val hhmm = Pattern.compile("(\\d{1,2})[:٫](\\d{1,2})").matcher(text)
        if (hhmm.find()) {
            val h = hhmm.group(1)?.toIntOrNull() ?: return null
            val m = hhmm.group(2)?.toIntOrNull() ?: 0
            return h.coerceIn(0, 23) to m.coerceIn(0, 59)
        }
        val hourOnly = Pattern.compile("ساعت\\s*(\\d{1,2})").matcher(text)
        if (hourOnly.find()) {
            val h = hourOnly.group(1)?.toIntOrNull() ?: return null
            return h.coerceIn(0, 23) to 0
        }
        return null
    }

    private fun extractJalaliDate(text: String): Triple<Int, Int, Int>? {
        val matcher = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})").matcher(text)
        return if (matcher.find()) {
            val y = matcher.group(1)?.toIntOrNull() ?: return null
            val m = matcher.group(2)?.toIntOrNull() ?: return null
            val d = matcher.group(3)?.toIntOrNull() ?: return null
            Triple(y, m, d)
        } else null
    }

    private fun extractRepeat(text: String): Pair<RepeatPattern, List<Int>> {
        if (text.contains("هر روز") || text.contains("روزانه")) return RepeatPattern.DAILY to emptyList()
        if (text.contains("هفتگی") || text.contains("هر هفته")) return RepeatPattern.WEEKLY to emptyList()
        if (text.contains("ماهانه") || text.contains("هر ماه")) return RepeatPattern.MONTHLY to emptyList()
        if (text.contains("سالیانه") || text.contains("هر سال")) return RepeatPattern.YEARLY to emptyList()
        if (text.contains("روزهای کاری")) return RepeatPattern.WEEKDAYS to emptyList()
        if (text.contains("آخر هفته")) return RepeatPattern.WEEKENDS to emptyList()

        // روزهای خاص هفته: جمعه‌ها، دوشنبه‌ها ...
        val matchedDays = dayMap.filter { text.contains(it.key) }.values.map { toCustomDayIndex(it) }
        if (matchedDays.isNotEmpty()) {
            return RepeatPattern.CUSTOM to matchedDays
        }
        return RepeatPattern.ONCE to emptyList()
    }

    // Calendar.DAY_OF_WEEK (1=یکشنبه) -> 0..6 برای custom
    private fun toCustomDayIndex(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            else -> 0
        }
    }

    private fun extractOffsetMinutes(text: String): Int? {
        return when {
            text.contains("یک ساعت قبل") || text.contains("1 ساعت قبل") -> 60
            text.contains("نیم ساعت قبل") || text.contains("30 دقیقه قبل") -> 30
            else -> null
        }
    }
}
