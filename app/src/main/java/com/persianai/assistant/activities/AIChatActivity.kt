package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.databinding.ActivityAichatBinding
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import kotlinx.coroutines.launch
import java.io.File

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
        
        // Setup voice button: focus the message input when tapped
        chatBinding.voiceButton.setOnClickListener {
            try {
                getMessageInput().requestFocus()
            } catch (_: Exception) { }
        }

        // Setup unified VoiceActionButton if present
        try {
            val vab = findViewById<com.persianai.assistant.ui.VoiceActionButton>(com.persianai.assistant.R.id.voiceActionButton)
                vab?.setListener(object : com.persianai.assistant.ui.VoiceActionButton.Listener {
                    override fun onRecordingStarted() {
                        chatBinding.voiceButton.alpha = 0.5f
                    }

                    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                        chatBinding.voiceButton.alpha = 1.0f
                        transcribeAudio(audioFile)
                    }

                    override fun onTranscript(text: String) {
                        chatBinding.voiceButton.alpha = 1.0f
                        chatBinding.messageInput.setText(text)
                        sendMessage()
                    }

                    override fun onRecordingError(error: String) {
                        chatBinding.voiceButton.alpha = 1.0f
                    }
                })
        } catch (e: Exception) {
            // ignore if view not present
        }
    }
    
    override fun getRecyclerView(): RecyclerView = chatBinding.chatRecyclerView
    override fun getMessageInput(): TextInputEditText = chatBinding.messageInput
    override fun getSendButton(): View = chatBinding.sendButton
    override fun getVoiceButton(): View = chatBinding.voiceButton
    
    override fun getSystemPrompt(): String = "دستیار هوشمند فارسی"
    
    override fun onVoiceRecordingStarted() {
        super.onVoiceRecordingStarted()
        // Update UI to show recording
        chatBinding.voiceButton.alpha = 0.5f
    }
    
    override fun onVoiceRecordingCompleted(audioFile: File, durationMs: Long) {
        super.onVoiceRecordingCompleted(audioFile, durationMs)
        chatBinding.voiceButton.alpha = 1.0f
        
        // Process audio file
        lifecycleScope.launch {
            try {
                val messageText = "🎙️ ضبط صوتی (${durationMs / 1000} ثانیه)"
                getMessageInput().setText(messageText)
                sendMessage()
            } catch (e: Exception) {
                Toast.makeText(this@AIChatActivity, "خطا در پردازش صوت", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onVoiceRecordingError(error: String) {
        super.onVoiceRecordingError(error)
        chatBinding.voiceButton.alpha = 1.0f
    }
}
