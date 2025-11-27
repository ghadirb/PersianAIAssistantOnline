package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.adapters.ChatAdapter
import com.persianai.assistant.databinding.ActivityAichatBinding
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import kotlinx.coroutines.launch

class AIChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAichatBinding
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAichatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupSendButton()
        
        addMessage(ChatMessage(role = MessageRole.ASSISTANT, content = "سلام! چطور کمکتون کنم؟"))
    }
    
    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = adapter
    }
    
    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addMessage(ChatMessage(role = MessageRole.USER, content = text))
                binding.messageInput.text?.clear()
                
                lifecycleScope.launch {
                    val response = "این یک پاسخ نمونه است"
                    addMessage(ChatMessage(role = MessageRole.ASSISTANT, content = response))
                }
            }
        }
    }
    
    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        binding.chatRecyclerView.scrollToPosition(messages.size - 1)
    }
}
