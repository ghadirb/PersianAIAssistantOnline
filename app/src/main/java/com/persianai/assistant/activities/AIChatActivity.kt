package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.databinding.ActivityAichatBinding
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole

class AIChatActivity : BaseChatActivity() {
    
    private lateinit var chatBinding: ActivityAichatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatBinding = ActivityAichatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)
        
        setupChatUI()
        addMessage(ChatMessage(role = MessageRole.ASSISTANT, content = "سلام! چطور کمکتون کنم؟"))

        val preset = intent.getStringExtra("presetMessage")?.takeIf { it.isNotBlank() }
        if (preset != null) {
            chatBinding.messageInput.setText(preset)
            sendMessage()
        }
    }
    
    override fun getRecyclerView(): RecyclerView = chatBinding.chatRecyclerView
    override fun getMessageInput(): TextInputEditText = chatBinding.messageInput
    override fun getSendButton(): View = chatBinding.sendButton
    override fun getVoiceButton(): View = chatBinding.voiceButton
    
    override fun getSystemPrompt(): String = "دستیار هوشمند فارسی"
}
