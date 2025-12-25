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

    override fun shouldUseOnlinePriority(): Boolean = true

    override fun getModuleIdForPrompt(): String = "culture"

    override fun getSystemPrompt(): String {
        return """
        شما دستیار «پیشنهاد فرهنگی» هستید.
        تمرکز شما روی پیشنهاد فیلم/سریال/کتاب/پادکست/دوره آموزشی است.

        قواعد پاسخ:
        - همیشه فارسی.
        - پاسخ‌ها باید متغیر و متناسب با ورودی کاربر باشند؛ از متن‌های ثابت تکراری پرهیز کن.
        - اول 1 تا 3 سوال کوتاه برای دقیق‌کردن سلیقه بپرس (اگر اطلاعات کافی نیست).
        - سپس 5 تا 10 پیشنهاد مشخص بده (عنوان + دلیل کوتاه + مناسب برای چه کسی).
        - اگر کاربر ژانر/حال‌وهوا/سن/محدودیت زمانی/پلتفرم را گفت، دقیقاً همان را رعایت کن.
        """.trimIndent()
    }

    override fun offlineDomainRespond(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        val lower = t.lowercase()

        val wantsBook = lower.contains("کتاب") || lower.contains("رمان") || lower.contains("مطالعه")
        val wantsFilm = lower.contains("فیلم") || lower.contains("سریال")

        if (wantsBook) {
            return "برای پیشنهاد کتاب، لطفاً ژانر و سطح را مشخص کنید (مثلاً: «کتاب رمان داستانی کوتاه» یا «کتاب روانشناسی برای یادگیری»)."
        }

        if (wantsFilm) {
            return "برای پیشنهاد فیلم/سریال، لطفاً ژانر و حال‌وهوا را مشخص کنید (مثلاً: «فیلم درام طولانی» یا «سریال کمدی خانوادگی»)."
        }

        return null
    }

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
