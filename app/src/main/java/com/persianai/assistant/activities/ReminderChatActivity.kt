package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.models.Reminder
import com.persianai.assistant.utils.PersianDate
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

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
                content = "سلام! برای تنظیم یادآوری، فقط کافیه بگی. مثلا: «فردا ساعت ۱۰ صبح یادم بنداز جلسه دارم»"
            )
        )
    }

    override fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView = chatBinding.recyclerView
    override fun getMessageInput(): com.google.android.material.textfield.TextInputEditText = chatBinding.messageInput
    override fun getSendButton(): View = chatBinding.sendButton
    override fun getVoiceButton(): View = chatBinding.voiceButton

    override fun getSystemPrompt(): String {
        return """
        شما یک دستیار هوشمند متخصص در زمینه مدیریت یادآوری‌ها هستید.
        وظیفه شما این است که درخواست‌های کاربر را تحلیل کرده و آن‌ها را به یک ساختار JSON مشخص برای افزودن یادآوری تبدیل کنید.
        قوانین:
        - همیشه یک آبجکت JSON با فیلد `action` برگردان.
        - اگر اطلاعات کافی نبود (مثلاً زمان یا متن یادآوری)، با یک سوال واضح از کاربر بپرس.
        اکشن‌های پشتیبانی‌شده:
        1) افزودن یادآوری:
           {"action":"add_reminder", "time":"HH:mm", "date":"YYYY/MM/DD", "message":"متن یادآوری", "repeat":"none|daily|weekly|monthly"}
        """
    }

    override suspend fun handleRequest(text: String): String {
        val responseJson = super.handleRequest(text)
        return withContext(Dispatchers.Main) {
            try {
                // استخراج JSON از پاسخ (ممکن است بین ``` باشد)
                val jsonStr = extractJsonFromResponse(responseJson)
                val json = Gson().fromJson(jsonStr, JsonObject::class.java)
                
                if (json.has("action") && json.get("action").asString == "add_reminder") {
                    val message = json.get("message").asString
                    val time = if (json.has("time")) json.get("time").asString else "09:00"
                    val dateStr = if (json.has("date")) json.get("date").asString else ""
                    val repeatStr = if (json.has("repeat")) json.get("repeat").asString else "none"
                    
                    // تنظیم زمان
                    val calendar = Calendar.getInstance()
                    try {
                        val parts = time.split(":")
                        if (parts.size >= 2) {
                            calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                            calendar.set(Calendar.MINUTE, parts[1].toInt())
                        }
                    } catch (e: Exception) {
                        calendar.set(Calendar.HOUR_OF_DAY, 9)
                        calendar.set(Calendar.MINUTE, 0)
                    }
                    calendar.set(Calendar.SECOND, 0)
                    
                    // اگر زمان گذشته، برای فردا تنظیم کن
                    if (calendar.timeInMillis < System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    
                    // تبدیل repeat
                    val repeatPattern = when (repeatStr.lowercase()) {
                        "daily" -> SmartReminderManager.RepeatPattern.DAILY
                        "weekly" -> SmartReminderManager.RepeatPattern.WEEKLY
                        "monthly" -> SmartReminderManager.RepeatPattern.MONTHLY
                        "yearly" -> SmartReminderManager.RepeatPattern.YEARLY
                        else -> SmartReminderManager.RepeatPattern.ONCE
                    }
                    
                    // ساخت یادآوری
                    val mgr = SmartReminderManager(this@ReminderChatActivity)
                    if (repeatPattern == SmartReminderManager.RepeatPattern.ONCE) {
                        mgr.createSimpleReminder(
                            title = message,
                            description = "",
                            triggerTime = calendar.timeInMillis
                        )
                    } else {
                        mgr.createRecurringReminder(
                            title = message,
                            description = "",
                            firstTriggerTime = calendar.timeInMillis,
                            repeatPattern = repeatPattern
                        )
                    }
                    
                    "✅ یادآوری «$message» برای ساعت $time تنظیم شد."
                } else {
                    responseJson // Return the raw JSON if it's not an add_reminder action
                }
            } catch (e: Exception) {
                responseJson // If parsing fails, return the original response
            }
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        // جستجو برای JSON بین { و }
        val startIdx = response.indexOf('{')
        val endIdx = response.lastIndexOf('}')
        
        return if (startIdx >= 0 && endIdx > startIdx) {
            response.substring(startIdx, endIdx + 1)
        } else {
            response
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
