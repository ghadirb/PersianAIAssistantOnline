package com.persianai.assistant.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.databinding.ActivityAichatBinding
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.LatLng

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
        
        // پیام خوشامد
        addMessage("سلام! من دستیار هوشمند مسیریابی هستم. چطور می‌تونم کمکتون کنم؟", false)
    }
    
    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = adapter
    }
    
    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            if (message.isNotBlank()) {
                sendMessage(message)
                binding.messageInput.text?.clear()
            }
        }
    }
    
    private fun sendMessage(message: String) {
        addMessage(message, true)
        
        lifecycleScope.launch {
            try {
                val response = getAIResponse(message)
                addMessage(response, false)
            } catch (e: Exception) {
                addMessage("متأسفم، خطایی رخ داد.", false)
            }
        }
    }
    
    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(ChatMessage(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)
        binding.chatRecyclerView.scrollToPosition(messages.size - 1)
    }
    
    private suspend fun getAIResponse(message: String): String {
        // دسترسی به NavigationActivity
        val navActivity = NavigationActivity.instance
        
        // پاسخ‌های هوشمند با کنترل واقعی
        return when {
            message.contains("مسیر") || message.contains("ناوبری") || message.contains("جاده") -> {
                val loc = navActivity?.currentLocation
                if (loc != null) {
                    "✅ مکان فعلی شما: ${String.format("%.4f, %.4f", loc.latitude, loc.longitude)}\nبرای مسیریابی، روی نقشه Long Press کنید."
                } else {
                    "⚠️ در حال دریافت مکان شما... لطفاً چند لحظه صبر کنید."
                }
            }
            
            message.contains("ذخیره") || message.contains("save") ->
                "برای ذخیره یک مکان، بعد از Long Press روی نقشه، گزینه 'ذخیره مکان' رو بزنید."
            
            message.contains("جستجو") || message.contains("search") ->
                "برای جستجو، از تب 'جستجو' در پایین صفحه استفاده کنید. می‌تونید نام هر مکانی رو جستجو کنید."
            
            message.contains("ترافیک") || message.contains("traffic") ->
                "برای مشاهده ترافیک، روی دکمه FAB ترافیک (پایین راست) کلیک کنید."
            
            message.contains("سلام") || message.contains("hello") || message.contains("hi") ->
                "سلام! خوش اومدید. من اینجام که به شما در مسیریابی کمک کنم. چه سوالی دارید?"
            
            message.contains("راهنما") || message.contains("help") ->
                "امکانات برنامه:\n• Long Press روی نقشه → انتخاب مقصد\n• تب جستجو → یافتن مکان\n• ذخیره مکان‌های مورد علاقه\n• مشاهده 3 مسیر پیشنهادی"
            
            else -> "متوجه نشدم. می‌تونید سوالتون رو واضح‌تر بپرسید یا کلمه 'راهنما' رو تایپ کنید."
        }
    }
    
    data class ChatMessage(val text: String, val isUser: Boolean)
}
