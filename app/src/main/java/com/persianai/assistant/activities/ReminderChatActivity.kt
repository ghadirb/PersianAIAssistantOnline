package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderChatActivity : BaseChatActivity() {

    private lateinit var smartReminderManager: SmartReminderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar((binding as ActivityChatBinding).toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "چت با دستیار یادآوری"

        smartReminderManager = SmartReminderManager(this)

        setupChatUI()

        addMessage(com.persianai.assistant.models.ChatMessage(role = MessageRole.ASSISTANT, content = "سلام! برای تنظیم یادآوری، فقط کافیه بگی. مثلا: «فردا ساعت ۱۰ صبح یادم بنداز جلسه دارم»"))
    }

    override fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView = (binding as ActivityChatBinding).recyclerView
    override fun getMessageInput(): com.google.android.material.textfield.TextInputEditText = (binding as ActivityChatBinding).messageInput
    override fun getSendButton(): View = (binding as ActivityChatBinding).sendButton
    override fun getVoiceButton(): View = (binding as ActivityChatBinding).voiceButton

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
                val json = Gson().fromJson(responseJson, JsonObject::class.java)
                if (json.has("action") && json.get("action").asString == "add_reminder") {
                    val message = json.get("message").asString
                    // TODO: Parse date and time correctly
                    smartReminderManager.createSimpleReminder(message, System.currentTimeMillis() + 60000) // Placeholder: 1 minute from now
                    "✅ یادآوری «$message» با موفقیت تنظیم شد."
                } else {
                    responseJson // Return the raw JSON if it's not an add_reminder action
                }
            } catch (e: Exception) {
                responseJson // If parsing fails, return the original response
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
