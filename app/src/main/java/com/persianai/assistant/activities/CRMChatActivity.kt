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

    private lateinit var binding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        
        supportActionBar?.apply {
            title = "دفتر مشتریان"
            setDisplayHomeAsUpEnabled(true)
        }
        
        super.onCreate(savedInstanceState)
        
        // دیالوگ شروع مختص
        val initialMessage = "سلام! من دستیار دفتر مشتریان شما هستم.\n\n" +
                "من اینجا هستم تا:\n" +
                "👥 مشتریان و اطلاعات آن‌ها را ثبت کنیم\n" +
                "📞 تماس‌ها و پیگیری‌های شما را مدیریت کنم\n" +
                "📝 یادداشت‌ها و یادآوری‌های مهم را حفظ کنم\n" +
                "📊 خلاصه‌ای از روند فروش و مراحل کار را دنبال کنم\n\n" +
                "چی می‌تونم برات انجام بدم؟ (مثل افزودن مشتری، ایجاد جدول، خلاصه‌سازی...)"
        
        addInitialMessage(initialMessage)
        setupChatUI()
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true
    
    private fun addInitialMessage(message: String) {
        lifecycleScope.launch {
            messages.add(
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = message,
                    isOffline = false
                )
            )
            if (this@CRMChatActivity::chatAdapter.isInitialized) {
                chatAdapter.notifyItemInserted(messages.size - 1)
                getRecyclerView().scrollToPosition(messages.size - 1)
            }
        }
    }
    
    override fun getRecyclerView(): RecyclerView {
        return binding.messagesRecyclerView
    }
    
    override fun getMessageInput(): TextInputEditText {
        return binding.messageInput
    }
    
    override fun getSendButton(): View {
        return binding.sendButton
    }
    
    override fun getVoiceButton(): View {
        return binding.voiceButton
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
