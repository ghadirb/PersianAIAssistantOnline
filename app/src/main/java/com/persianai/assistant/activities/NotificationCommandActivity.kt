package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.core.AIIntentController
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.databinding.ActivityNotificationCommandBinding
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * نمایش متن فرمان ضبط‌شده از نوتیفیکیشن، امکان ویرایش/لغو و اجرای فرمان
 * شامل تأیید تماس و انتخاب شماره در صورت وجود چند شماره.
 */
class NotificationCommandActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSCRIPT = "extra_transcript"
        const val EXTRA_MODE = "extra_mode"
    }

    private lateinit var binding: ActivityNotificationCommandBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationCommandBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        val transcript = intent?.getStringExtra(EXTRA_TRANSCRIPT).orEmpty()
        binding.inputTranscript.setText(transcript)
        binding.inputTranscript.setSelection(transcript.length)
        binding.sttWarning.text = "تشخیص گفتار ممکن است خطا داشته باشد. در صورت نیاز، متن را اصلاح کنید."
        binding.sttWarning.visibility = View.VISIBLE

        binding.btnCancel.setOnClickListener { finish() }
        binding.btnRun.setOnClickListener { runCommand() }
    }

    private fun runCommand() {
        val text = binding.inputTranscript.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "متن خالی است", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRun.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.statusText.text = "در حال پردازش..."

        lifecycleScope.launch {
            try {
                val controller = AIIntentController(this@NotificationCommandActivity)
                val intentDetected = controller.detectIntentFromTextAsync(text, "notification")
                val result = controller.handle(
                    AIIntentRequest(
                        intent = intentDetected,
                        source = AIIntentRequest.Source.NOTIFICATION,
                        workingModeName = prefs.getWorkingMode().name
                    )
                )

                binding.statusText.text = result.text

                if (result.actionType == "confirm_call") {
                    handleCallConfirmation(result.actionData)
                } else {
                    Toast.makeText(this@NotificationCommandActivity, "تمام شد", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                binding.statusText.text = "❌ خطا: ${e.message}"
                binding.btnRun.isEnabled = true
                binding.btnCancel.isEnabled = true
            }
        }
    }

    private fun handleCallConfirmation(actionData: String?) {
        val numbers = actionData.orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (numbers.isEmpty()) {
            Toast.makeText(this, "شماره‌ای یافت نشد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val items = numbers.toTypedArray()
        val listView = ListView(this).apply {
            adapter = ArrayAdapter(this@NotificationCommandActivity, android.R.layout.simple_list_item_1, items)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب شماره تماس")
            .setView(listView)
            .setNegativeButton("لغو") { d, _ ->
                d.dismiss()
                finish()
            }
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            val number = items[position]
            confirmAndDial(number)
        }

        dialog.show()
    }

    private fun confirmAndDial(number: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("تأیید تماس")
            .setMessage("با شماره زیر تماس برقرار شود؟\n$number")
            .setPositiveButton("تماس") { _, _ ->
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "مجوز تماس داده نشده", Toast.LENGTH_SHORT).show()
                    finish()
                    return@setPositiveButton
                }
                try {
                    val i = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$number")
                    }
                    startActivity(i)
                } catch (_: Exception) {
                } finally {
                    finish()
                }
            }
            .setNegativeButton("لغو") { _, _ -> finish() }
            .show()
    }
}
