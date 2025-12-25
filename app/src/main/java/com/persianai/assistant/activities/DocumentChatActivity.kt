package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import kotlinx.coroutines.launch

/**
 * بانک اسناد (Document Management)
 * یک چت جداگانه برای مدیریت قراردادها و اسناد مهم
 */
class DocumentChatActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatBinding = ActivityChatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)
        setSupportActionBar(chatBinding.toolbar)
        
        supportActionBar?.apply {
            title = "بانک اسناد"
            setDisplayHomeAsUpEnabled(true)
        }

        setupChatUI()
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true

    override fun getModuleIdForPrompt(): String = "documents"

    override fun getSystemPrompt(): String {
        return """
        شما دستیار «بانک اسناد» هستید.
        کار شما کمک به مدیریت اسناد/قراردادهاست: خلاصه‌سازی، چک‌لیست نکات، استخراج بندهای مهم، برچسب‌گذاری و پیشنهاد نام‌گذاری فایل.

        قواعد:
        - همیشه فارسی.
        - اگر متن سند موجود نیست، اول درخواست کن کاربر متن/عکس/بخش مهم را ارسال کند.
        - خروجی‌ها را ساختارمند بده: تیترها، bulletها، چک‌لیست.
        - از جواب ثابت تکراری پرهیز کن و بر اساس سند/درخواست دقیق پاسخ بده.
        """.trimIndent()
    }

    override fun getIntroMessage(): String {
        return "سلام! من دستیار بانک اسناد شما هستم.\n\n" +
            "من اینجا هستم تا:\n" +
            "📄 قراردادها و اسناد مهم را مدیریت کنیم\n" +
            "🏷️ فایل‌ها و اسناد را برچسب‌گذاری کنیم\n" +
            "🔍 سریع اسناد مورد نیاز را پیدا کنیم\n" +
            "📋 خلاصه و خلاصه‌سازی از اسناد انجام دهیم\n\n" +
            "چی می‌تونم برات انجام بدم؟ (مثل ایجاد چک‌لیست، برچسب‌گذاری، خلاصه‌سازی...)"
    }
    
    override fun getRecyclerView(): RecyclerView {
        return chatBinding.messagesRecyclerView
    }
    
    override fun getMessageInput(): TextInputEditText {
        return chatBinding.messageInput
    }
    
    override fun getSendButton(): View {
        return chatBinding.sendButton
    }
    
    override fun getVoiceButton(): View {
        return chatBinding.voiceButton
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
