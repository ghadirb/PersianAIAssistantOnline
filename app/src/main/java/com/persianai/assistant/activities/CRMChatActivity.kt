package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.adapters.ChatAdapter
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * دفتر مشتریان (CRM - Customer Relationship Management)
 * یک چت جداگانه برای مدیریت مشتریان و روابط کسب‌وکاری
 */
class CRMChatActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatBinding = ActivityChatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)
        setSupportActionBar(chatBinding.toolbar)
        
        supportActionBar?.apply {
            title = "دفتر مشتریان"
            setDisplayHomeAsUpEnabled(true)
        }

        setupChatUI()
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true

    override fun getModuleIdForPrompt(): String = "crm"

    override fun getSystemPrompt(): String {
        return """
        شما دستیار «دفتر مشتریان» (CRM) هستید.
        هدف: مدیریت مشتریان، تماس‌ها، پیگیری‌های فروش، یادداشت‌ها و مراحل کار (Pipeline).

        قواعد:
        - همیشه فارسی.
        - پاسخ‌ها باید متناسب با درخواست کاربر باشند و از متن‌های تکراری پرهیز شود.
        - اگر اطلاعات کافی نیست، سوالات مشخص بپرس: نام مشتری، نوع محصول/خدمت، مرحله فروش، زمان تماس قبلی.
        - خروجی‌ها را ساختارمند بده: جدول، لیست گام‌های بعدی، یادآوری‌های مهم.
        - در صورت نیاز، برای پیگیری‌های بعدی اطلاعات مختصری ثبت کن.
        """.trimIndent()
    }

    override fun offlineDomainRespond(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        val lower = t.lowercase()

        if (lower.contains("مشتری") || lower.contains("مشتریان") || lower.contains("client")) {
            return "برای مدیریت مشتری، لطفاً نام مشتری و نوع محصول/خدمت مورد نظر را مشخص کنید."
        }

        if (lower.contains("فروش") || lower.contains("پیگیری") || lower.contains("follow")) {
            return "برای پیگیری فروش، لطفاً مرحله فعلی (چاپ، منتظر تأیید، بسته‌شده) و تاریخ تماس آخر را بگویید."
        }

        if (lower.contains("یادداشت") || lower.contains("note")) {
            return "برای افزودن یادداشت مشتری، لطفاً نام مشتری و متن یادداشت را وارد کنید."
        }

        return null
    }

    override fun getIntroMessage(): String {
        return "سلام! من دستیار دفتر مشتریان شما هستم.\n\n" +
            "من اینجا هستم تا:\n" +
            "👥 مشتریان و اطلاعات آن‌ها را ثبت کنیم\n" +
            "📞 تماس‌ها و پیگیری‌های شما را مدیریت کنم\n" +
            "📝 یادداشت‌ها و یادآوری‌های مهم را حفظ کنم\n" +
            "📊 خلاصه‌ای از روند فروش و مراحل کار را دنبال کنم\n\n" +
            "چی می‌تونم برات انجام بدم؟ (مثل افزودن مشتری، ایجاد جدول، خلاصه‌سازی...)"
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
