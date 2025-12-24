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

        // دیالوگ شروع مختص
        val initialMessage = "سلام! من دستیار دفتر مشتریان شما هستم.\n\n" +
                "من اینجا هستم تا:\n" +
                "👥 مشتریان و اطلاعات آن‌ها را ثبت کنیم\n" +
                "📞 تماس‌ها و پیگیری‌های شما را مدیریت کنم\n" +
                "📝 یادداشت‌ها و یادآوری‌های مهم را حفظ کنم\n" +
                "📊 خلاصه‌ای از روند فروش و مراحل کار را دنبال کنم\n\n" +
                "چی می‌تونم برات انجام بدم؟ (مثل افزودن مشتری، ایجاد جدول، خلاصه‌سازی...)"

        addMessage(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = initialMessage
            )
        )
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true
    
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
