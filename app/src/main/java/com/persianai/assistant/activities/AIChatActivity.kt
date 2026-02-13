package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.databinding.ActivityAichatBinding
import com.persianai.assistant.config.RemoteAIConfigManager
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import kotlinx.coroutines.launch
import java.io.File

class AIChatActivity : BaseChatActivity() {
    
    private lateinit var chatBinding: ActivityAichatBinding
    private var forceOnlineAnalysis: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatBinding = ActivityAichatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)

        // Toolbar for overflow (سه نقطه) menu parity with dashboard
        setSupportActionBar(chatBinding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        forceOnlineAnalysis = intent.getBooleanExtra("forceOnlineAnalysis", false)
        updateAdvancedBadge()

        setupChatUI()
        
        // ✅ بررسی API keys و نمایش پیام راهنما اگر موجود نیستند
        checkAndShowApiKeyWarning()
        
        addMessage(ChatMessage(role = MessageRole.ASSISTANT, content = "سلام! چطور کمکتون کنم؟"))

        showRemoteConfigMessagesPopupIfAny()

        val preset = intent.getStringExtra("presetMessage")?.takeIf { it.isNotBlank() }
        if (preset != null) {
            chatBinding.messageInput.setText(preset)
            sendMessage()
        }

        // ✅ Setup voice button with unified listener
        setupVoiceButton()

        // دسترسی سریع به بخش‌های داشبورد
        chatBinding.btnCalendar.setOnClickListener { startActivity(Intent(this, CalendarActivity::class.java)) }
        chatBinding.btnAccountingAdvanced.setOnClickListener { startActivity(Intent(this, AccountingAdvancedActivity::class.java)) }
        chatBinding.btnRemindersAdvanced.setOnClickListener { startActivity(Intent(this, AdvancedRemindersActivity::class.java)) }
        chatBinding.btnVoiceNav.setOnClickListener { startActivity(Intent(this, VoiceNavigationAssistantActivity::class.java)) }
        chatBinding.btnPsychology.setOnClickListener { startActivity(Intent(this, PsychologyChatActivity::class.java)) }
        chatBinding.btnCareer.setOnClickListener { startActivity(Intent(this, CareerChatActivity::class.java)) }
        chatBinding.btnCultural.setOnClickListener { startActivity(Intent(this, CulturalChatActivity::class.java)) }
        chatBinding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun showRemoteConfigMessagesPopupIfAny() {
        lifecycleScope.launch {
            try {
                val rc = RemoteAIConfigManager.getInstance(this@AIChatActivity)
                val cached = rc.loadCached()
                val msg = buildString {
                    val m = cached?.messages
                    val welcome = m?.welcome?.trim().orEmpty()
                    val ann = m?.global_announcement?.trim().orEmpty()
                    val offline = m?.offline_message?.trim().orEmpty()

                    if (welcome.isNotBlank()) append(welcome)
                    if (ann.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(ann)
                    }
                    if (offline.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append("پیام آفلاین: ")
                        append(offline)
                    }
                }.trim()

                if (msg.isNotBlank()) {
                    androidx.appcompat.app.AlertDialog.Builder(this@AIChatActivity)
                        .setTitle("پیام")
                        .setMessage(msg)
                        .setPositiveButton("باشه", null)
                        .show()
                }

                // refresh in background (non-blocking UI) for next launch
                try { rc.refreshAndCache() } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.w("AIChatActivity", "Remote message popup failed: ${e.message}")
            }
        }
    }
        
        // ✅ Setup voice button with unified listener
        setupVoiceButton()

        // دسترسی سریع به بخش‌های داشبورد
        chatBinding.btnCalendar.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        chatBinding.btnAccountingAdvanced.setOnClickListener {
            startActivity(Intent(this, AccountingAdvancedActivity::class.java))
        }
        chatBinding.btnRemindersAdvanced.setOnClickListener {
            startActivity(Intent(this, AdvancedRemindersActivity::class.java))
        }
        chatBinding.btnVoiceNav.setOnClickListener {
            startActivity(Intent(this, VoiceNavigationAssistantActivity::class.java))
        }
        chatBinding.btnPsychology.setOnClickListener {
            startActivity(Intent(this, PsychologyChatActivity::class.java))
        }
        chatBinding.btnCareer.setOnClickListener {
            startActivity(Intent(this, CareerChatActivity::class.java))
        }
        chatBinding.btnCultural.setOnClickListener {
            startActivity(Intent(this, CulturalChatActivity::class.java))
        }
        chatBinding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun checkAndShowApiKeyWarning() {
        val hasKeys = prefsManager.getAPIKeys().isNotEmpty()
        if (!hasKeys) {
            android.widget.Toast.makeText(
                this,
                "⚠️ هیچ کلید API تنظیم نشده است.\n\nبرای استفاده از پاسخ‌های هوشمند:\n" +
                "1. به تنظیمات برو\n2. یک کلید API اضافه کن\n3. دوباره تلاش کن",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun updateAdvancedBadge() {
        chatBinding.advancedBadge?.apply {
            visibility = if (forceOnlineAnalysis) View.VISIBLE else View.GONE
            text = "⚡ تحلیل پیشرفته فعال شد"
        }
    }

    // ✅ Setup voice button with proper listener
    private fun setupVoiceButton() {
        try {
            chatBinding.voiceButton.setListener(object : com.persianai.assistant.ui.VoiceActionButton.Listener {
                override fun onRecordingStarted() {
                    android.util.Log.d("AIChatActivity", "🎙️ Recording started")
                    chatBinding.voiceButton.alpha = 0.5f
                }
                
                override fun onRecordingFinished(audioPath: String) {
                    android.util.Log.d("AIChatActivity", "🎙️ Recording finished: $audioPath")
                    chatBinding.voiceButton.alpha = 1.0f
                    handleTranscript(audioPath)
                }
                
                override fun onRecordingError(error: String) {
                    android.util.Log.e("AIChatActivity", "🎙️ Recording error: $error")
                    chatBinding.voiceButton.alpha = 1.0f
                    android.widget.Toast.makeText(this@AIChatActivity, "خطا در ضبط صدا: $error", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            android.util.Log.d("AIChatActivity", "✅ Voice button listener configured")
        } catch (e: Exception) {
            android.util.Log.e("AIChatActivity", "❌ Error setting up voice button", e)
        }
    }
    override fun getSendButton(): View = chatBinding.sendButton
    override fun getVoiceButton(): View = chatBinding.voiceButton
    
    override fun getSystemPrompt(): String = "دستیار هوشمند فارسی"

    override fun shouldUseOnlinePriority(): Boolean = true
}
