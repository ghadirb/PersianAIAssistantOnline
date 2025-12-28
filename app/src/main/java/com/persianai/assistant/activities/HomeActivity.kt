package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.core.AIIntentController
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.intent.ReminderListIntent
import com.persianai.assistant.databinding.ActivityHomeBinding
import com.persianai.assistant.ui.VoiceActionButton
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var controller: AIIntentController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = AIIntentController(this)

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
