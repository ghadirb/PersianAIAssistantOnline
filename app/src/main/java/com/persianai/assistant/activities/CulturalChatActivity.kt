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
 * پیشنهاد فرهنگی (Cultural Recommendations)
 * یک چت جداگانه برای دریافت پیشنهادات کتاب، فیلم و دوره آموزشی
 */
class CulturalChatActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityChatBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatBinding = ActivityChatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)
        setSupportActionBar(chatBinding.toolbar)
        
        supportActionBar?.apply {
            title = "پیشنهاد فرهنگی"
            setDisplayHomeAsUpEnabled(true)
        }

        setupChatUI()
    }

    override fun offlineDomainRespond(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        val lower = t.lowercase()

        val wantsBook = lower.contains("کتاب") || lower.contains("رمان") || lower.contains("مطالعه")
        val wantsFilm = lower.contains("فیلم") || lower.contains("سریال")

        if (wantsBook) {
            return "برای پیشنهاد کتاب، ۳ چیز رو بگو تا دقیق‌تر پیشنهاد بدم:\n" +
                "1) ژانر (مثلاً انگیزشی/روانشناسی/داستانی/تاریخی)\n" +
                "2) سطح (سبک/متوسط/سنگین)\n" +
                "3) هدف (لذت/یادگیری/تمرکز/آرامش)\n\n" +
                "اگر نمی‌دونی، همین الان بگو: «کتاب سبک برای آرامش» یا «کتاب انگیزشی کوتاه»."
        }

        if (wantsFilm) {
            return "برای پیشنهاد فیلم/سریال، بگو:\n" +
                "- ژانر (درام/کمدی/هیجان/معمایی)\n" +
                "- حوصله (کوتاه/سریال طولانی)\n" +
                "- محدودیت سنی؟\n\n" +
                "مثلاً بنویس: «فیلم معمایی کوتاه» یا «سریال کمدی خانوادگی»."
        }

        return "آفلاین هم می‌تونم پیشنهاد فرهنگی بدم.\n\n" +
            "اگر بگی «کتاب» می‌خوای یا «فیلم»، و ژانر/حال‌وهوا رو مشخص کنی، دقیق‌تر پیشنهاد می‌دم.\n\n" +
            "مثال: «کتاب داستانی کوتاه»، «فیلم انگیزشی»، «سریال معمایی»."
    }
    
    override fun shouldUseOnlinePriority(): Boolean = true

    override fun getIntroMessage(): String {
        return "سلام! من دستیار فرهنگی و یادگیری شما هستم.\n\n" +
            "من اینجا هستم تا:\n" +
            "📚 کتاب‌های الهام‌بخش برای شما پیشنهاد دهم\n" +
            "🎬 فیلم‌های ارزشمندی را معرفی کنم\n" +
            "🎓 دوره‌های آموزشی متناسب با علایقتان پیدا کنم\n" +
            "💡 نویسندگان و فیلمسازان جدید را کشف کنید\n" +
            "\n" +
            "بهتر است علایقتان را بگویید تا بتوانم بهترین پیشنهادها را ارائه دهم."
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
