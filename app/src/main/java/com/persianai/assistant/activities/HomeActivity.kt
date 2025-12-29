package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.core.AIIntentController
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.intent.ReminderListIntent
import com.persianai.assistant.databinding.ActivityHomeBinding
import com.persianai.assistant.ui.VoiceActionButton
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var controller: AIIntentController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = AIIntentController(this)

        try {
            val prefsManager = PreferencesManager(this)
            val keys = prefsManager.getAPIKeys()
            if (keys.isNotEmpty() && keys.any { it.isActive }) {
                val sp = getSharedPreferences("api_keys", MODE_PRIVATE)
                val e = sp.edit()
                e.putString("liara_api_key", keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.LIARA && it.isActive }?.key)
                e.putString("openai_api_key", keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.OPENAI && it.isActive }?.key)
                e.putString("openrouter_api_key", keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.OPENROUTER && it.isActive }?.key)
                e.putString("aiml_api_key", keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.AIML && it.isActive }?.key)
                e.putString("claude_api_key", keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.ANTHROPIC && it.isActive }?.key)
                e.apply()
            }
        } catch (_: Exception) {
        }

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 4001)
            }
        } catch (_: Exception) {
        }

        handleIncomingIntent(intent)

        binding.sendButton.setOnClickListener { sendTextAsIntent() }

        binding.voiceActionButton.setListener(object : VoiceActionButton.Listener {
            override fun onRecordingStarted() {
                binding.statusText.text = "در حال ضبط..."
            }

            override fun onRecordingCompleted(audioFile: java.io.File, durationMs: Long) {
                binding.statusText.text = "در حال پردازش..."
            }

            override fun onTranscript(text: String) {
                binding.inputEdit.setText(text)
                sendTextAsIntent()
            }

            override fun onRecordingError(error: String) {
                binding.statusText.text = error
            }
        })

        binding.shortcutReminders.setOnClickListener {
            lifecycleScope.launch {
                val req = AIIntentRequest(ReminderListIntent(), AIIntentRequest.Source.UI)
                val res = controller.handle(req)
                binding.outputText.text = res.text
            }
        }

        binding.shortcutSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.shortcutDashboard.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        binding.startVoiceConversation.setOnClickListener {
            try {
                val i = Intent(this, AIChatActivity::class.java)
                i.putExtra(com.persianai.assistant.activities.BaseChatActivity.EXTRA_START_VOICE_CONVERSATION, true)
                startActivity(i)
            } catch (_: Exception) {
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val openScreen = intent.getStringExtra("open_screen")
        if (openScreen == "reminder") {
            val title = intent.getStringExtra("reminder_title").orEmpty()
            val desc = intent.getStringExtra("reminder_description").orEmpty()
            if (title.isNotBlank() || desc.isNotBlank()) {
                binding.outputText.text = listOf(title, desc).filter { it.isNotBlank() }.joinToString("\n")
            }
        }
    }

    private fun sendTextAsIntent() {
        val text = binding.inputEdit.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "متنی وارد نشده", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val aiIntent = controller.detectIntentFromTextAsync(text)
            val req = AIIntentRequest(aiIntent, AIIntentRequest.Source.UI)
            val res = controller.handle(req)
            binding.outputText.text = res.text
            maybeHandleUiAction(res.actionType, res.actionData)
        }
    }

    private fun maybeHandleUiAction(actionType: String?, actionData: String?) {
        if (actionType == "confirm_call" && !actionData.isNullOrBlank()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "مجوز تماس داده نشده", Toast.LENGTH_SHORT).show()
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("تأیید تماس")
                .setMessage("آیا تماس برقرار شود؟\n$actionData")
                .setPositiveButton("تماس") { _, _ ->
                    try {
                        val i = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:$actionData")
                        }
                        startActivity(i)
                    } catch (_: Exception) {
                    }
                }
                .setNegativeButton("لغو", null)
                .show()
        }
    }
}
