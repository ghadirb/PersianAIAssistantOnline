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
                
                // بستن چت بعد از اجرای دستور
                if (message.contains("برو ") || message.contains("مسیر ") || message.contains("جستجو ")) {
                    kotlinx.coroutines.delay(1000)
                    finish()
                }
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
        val navActivity = NavigationActivity.instance
        
        return when {
            // جستجوی مکان و مسیریابی مستقیم - همه حالات
            message.contains("برو") || message.contains("مسیر") || 
            message.contains("مسیریابی") || message.contains("تا ") ||
            message.contains("ببر") -> {
                val location = extractLocation(message)
                if (location.isNotEmpty() && navActivity != null) {
                    navActivity.runOnUiThread {
                        navActivity.searchAndNavigateTo(location)
                    }
                    "🚗 در حال جستجو و مسیریابی به '$location'..."
                } else {
                    "⚠️ لطفاً نام مکان را مشخص کنید. مثل:\n• 'برو میدان آزادی'\n• 'مسیر برج میلاد'"
                }
            }
            
            message.contains("جستجو ") -> {
                val query = message.replace("جستجو", "").trim()
                if (query.isNotEmpty() && navActivity != null) {
                    navActivity.runOnUiThread {
                        navActivity.performDirectSearch(query)
                    }
                    "🔍 در حال جستجو برای '$query'..."
                } else {
                    "⚠️ لطفاً چیزی برای جستجو بنویسید"
                }
            }
            
            message.contains("مکان فعلی") -> {
                val loc = navActivity?.currentLocation
                if (loc != null) {
                    "📍 مکان فعلی شما:\n${String.format("%.6f, %.6f", loc.latitude, loc.longitude)}"
                } else {
                    "⚠️ در حال دریافت مکان..."
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
    
    private fun extractLocation(message: String): String {
        return message.replace("برو", "")
            .replace("مسیر", "").replace("مسیریابی", "")
            .replace("به", "").replace("تا", "")
            .replace("ببر", "").replace("بردن", "")
            .trim()
    }
    
    data class ChatMessage(val text: String, val isUser: Boolean)
}
