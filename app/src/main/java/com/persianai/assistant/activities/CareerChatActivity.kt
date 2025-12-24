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
 * مشاور مسیر شغلی و تحصیلی
 * یک چت جداگانه برای راهنمایی شغلی و تحصیلی
 */
class CareerChatActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatBinding = ActivityChatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)
        setSupportActionBar(chatBinding.toolbar)
        
        supportActionBar?.apply {
            title = "مشاور مسیر شغلی"
            setDisplayHomeAsUpEnabled(true)
        }

        setupChatUI()
    }

    override fun offlineDomainRespond(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        val lower = t.lowercase()

        if (lower.contains("رزوم") || lower.contains("cv") || lower.contains("مصاحبه")) {
            return "برای رزومه/مصاحبه، سریع بگو:\n" +
                "1) عنوان شغلی هدف\n" +
                "2) سابقه/مهارت‌های اصلی (۳ مورد)\n" +
                "3) شهر/نوع همکاری\n\n" +
                "بعدش من یک ساختار رزومه و چند جمله آماده برای معرفی در مصاحبه می‌دم."
        }

        if (lower.contains("برنامه") || lower.contains("مسیر") || lower.contains("یادگیری") || lower.contains("مهارت")) {
            return "برای طراحی مسیر شغلی آفلاین، این ۳ سوال رو جواب بده:\n\n" +
                "- به چه حوزه‌ای علاقه داری؟ (مثلاً برنامه‌نویسی، فروش، طراحی، حسابداری...)\n" +
                "- روزی چند ساعت می‌تونی وقت بذاری؟\n" +
                "- هدف: استخدام / فریلنس / ارتقای شغلی؟\n\n" +
                "با جواب‌هات یک پلن ۲ تا ۴ هفته‌ای می‌دم."
        }

        return "برای اینکه دقیق راهنمایی کنم، لطفاً یکی از این‌ها رو مشخص کن:\n" +
            "- علاقه/حوزه\n" +
            "- سطح فعلی (مبتدی/متوسط/حرفه‌ای)\n" +
            "- هدف (استخدام/فریلنس/ارتقا)"
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true

    override fun getIntroMessage(): String {
        return "سلام! من مشاور مسیر شغلی شما هستم.\n\n" +
            "من اینجا هستم تا:\n" +
            "🎯 بر اساس علایق و مهارت‌های شما بهترین راه‌حل را پیدا کنم\n" +
            "💼 درباره شغل‌ها و رشته‌های مختلف اطلاعات دهم\n" +
            "🚀 برنامه‌ای برای توسعه مهارت‌های شما بسازم\n\n" +
            "⚠️ توجه: نتایج این مشاوره تنها نقش راهنمایی دارد. تصمیم نهایی با شماست و بهتر است با یک مشاور حرفه‌ای نیز مشورت کنید.\n\n" +
            "اولاً، مسیری رو انتخاب کن که علاقه‌مند هستی: آموزش، شغل، یا تغییر مسیر موجود؟"
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
