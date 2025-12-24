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
 * مشاور روان شناسی و آرامش
 * یک چت جداگانه برای مشاوره روانی و مدیریت استرس
 */
class PsychologyChatActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatBinding = ActivityChatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)
        setSupportActionBar(chatBinding.toolbar)
        
        supportActionBar?.apply {
            title = "مشاور آرامش و خودشناسی"
            setDisplayHomeAsUpEnabled(true)
        }

        setupChatUI()
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true

    override fun getIntroMessage(): String {
        return "سلام! من مشاور آرامش شما هستم.\n\n" +
            "من اینجا هستم تا:\n" +
            "🎯 شما را در مدیریت استرس و اضطراب یاری دهم\n" +
            "💭 برای درک بهتر احساسات و فکرهایتان گوش دهم\n" +
            "🌱 فنون خود‌آگاهی و خودپذیری را یاد دهم\n\n" +
            "⚠️ توجه: من یک مشاور انسانی جایگزین نیستم. در شرایط اضطرار فوری با متخصص تماس بگیرید.\n\n" +
            "چی می‌تونم برات انجام بدم؟"
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
