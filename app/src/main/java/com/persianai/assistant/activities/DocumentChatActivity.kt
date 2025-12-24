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

    private lateinit var binding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        
        supportActionBar?.apply {
            title = "بانک اسناد"
            setDisplayHomeAsUpEnabled(true)
        }
        
        super.onCreate(savedInstanceState)
        
        // دیالوگ شروع مختص بانک اسناد
        val initialMessage = "سلام! من دستیار بانک اسناد شما هستم.\n\n" +
                "من اینجا هستم تا:\n" +
                "📄 قراردادها و اسناد مهم را مدیریت کنیم\n" +
                "🏷️ فایل‌ها و اسناد را برچسب‌گذاری کنیم\n" +
                "🔍 سریع اسناد مورد نیاز را پیدا کنیم\n" +
                "📋 خلاصه و خلاصه‌سازی از اسناد انجام دهیم\n\n" +
                "چی می‌تونم برات انجام بدم؟ (مثل ایجاد چک‌لیست، برچسب‌گذاری، خلاصه‌سازی...)"
        
        addInitialMessage(initialMessage)
        setupChatUI()
    }
    
    private fun addInitialMessage(message: String) {
        lifecycleScope.launch {
            messages.add(
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = message,
                    isOffline = false
                )
            )
            if (this@DocumentChatActivity::chatAdapter.isInitialized) {
                chatAdapter.notifyItemInserted(messages.size - 1)
                getRecyclerView().scrollToPosition(messages.size - 1)
            }
        }
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true
    
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
