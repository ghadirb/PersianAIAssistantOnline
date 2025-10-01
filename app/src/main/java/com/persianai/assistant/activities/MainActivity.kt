package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.adapters.ChatAdapter
import com.persianai.assistant.ai.AIClient
// import com.persianai.assistant.database.ChatDatabase // Temporarily disabled
import com.persianai.assistant.databinding.ActivityMainBinding
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.MessageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * صفحه اصلی چت
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var prefsManager: PreferencesManager
    private lateinit var messageStorage: MessageStorage
    private var aiClient: AIClient? = null
    private var currentModel: AIModel = AIModel.GPT_4O_MINI
    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("MainActivity", "onCreate started")
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            android.util.Log.d("MainActivity", "Layout inflated")

            setSupportActionBar(binding.toolbar)
            supportActionBar?.title = "دستیار هوش مصنوعی"
            
            android.util.Log.d("MainActivity", "Toolbar set")

            prefsManager = PreferencesManager(this)
            messageStorage = MessageStorage(this)
            
            android.util.Log.d("MainActivity", "Managers initialized")
            
            setupRecyclerView()
            android.util.Log.d("MainActivity", "RecyclerView setup")
            
            setupAIClient()
            android.util.Log.d("MainActivity", "AIClient setup")
            
            loadMessages()
            android.util.Log.d("MainActivity", "Messages loaded")
            
            setupListeners()
            android.util.Log.d("MainActivity", "Listeners setup")
            
            updateModelDisplay()
            android.util.Log.d("MainActivity", "Model display updated")
            
            // نمایش پیام خوش‌آمدگویی در اولین اجرا
            showFirstRunDialogIfNeeded()
            
            android.util.Log.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "FATAL ERROR in onCreate", e)
            
            // نمایش خطا به کاربر
            Toast.makeText(
                this,
                "خطای شروع برنامه: ${e.message}\n\nلطفاً برنامه را حذف و دوباره نصب کنید.",
                Toast.LENGTH_LONG
            ).show()
            
            // بستن برنامه
            finish()
        }
    }
    
    private fun showFirstRunDialogIfNeeded() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        
        if (isFirstRun) {
            MaterialAlertDialogBuilder(this)
                .setTitle("🤖 خوش آمدید!")
                .setMessage("""
                    به دستیار هوش مصنوعی فارسی خوش آمدید!
                    
                    برای استفاده از این برنامه:
                    • از منوی بالا → تنظیمات → کلیدهای API را اضافه کنید
                    • یا از حالت آزمایشی استفاده کنید
                    
                    امکانات:
                    ✅ چت با مدل‌های GPT-4o و Claude
                    ✅ تشخیص صوت فارسی
                    ✅ ذخیره تاریخچه گفتگوها
                """.trimIndent())
                .setPositiveButton("متوجه شدم") { _, _ ->
                    prefs.edit().putBoolean("is_first_run", false).apply()
                }
                .setCancelable(true)
                .show()
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupAIClient() {
        val apiKeys = prefsManager.getAPIKeys()
        if (apiKeys.isNotEmpty()) {
            aiClient = AIClient(apiKeys)
            currentModel = prefsManager.getSelectedModel()
        } else {
            Toast.makeText(this, "کلید API یافت نشد. لطفاً از تنظیمات اضافه کنید.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.voiceButton.setOnClickListener {
            checkAudioPermissionAndRecord()
        }

        binding.attachButton.setOnClickListener {
            showAttachmentOptions()
        }
    }

    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "لطفاً پیامی وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }

        if (aiClient == null) {
            Toast.makeText(this, "کلید API تنظیم نشده است", Toast.LENGTH_SHORT).show()
            return
        }

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text,
            timestamp = System.currentTimeMillis()
        )

        addMessage(userMessage)
        binding.messageInput.text?.clear()

        // نمایش نشانگر بارگذاری
        binding.sendButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val systemPrompt = prefsManager.getSystemPrompt()
                val response = aiClient!!.sendMessage(currentModel, messages, systemPrompt)
                
                addMessage(response)
                
                // ذخیره پیام‌ها
                withContext(Dispatchers.IO) {
                    messageStorage.saveMessage(userMessage)
                    messageStorage.saveMessage(response)
                }
                
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "خطا: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
                addMessage(errorMessage)
            } finally {
                binding.sendButton.isEnabled = true
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.recyclerView.smoothScrollToPosition(messages.size - 1)
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val savedMessages = withContext(Dispatchers.IO) {
                messageStorage.getAllMessages()
            }
            messages.addAll(savedMessages)
            chatAdapter.notifyDataSetChanged()
            if (messages.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun checkAudioPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "تشخیص صوت در دستگاه شما پشتیبانی نمی‌شود", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "صحبت کنید...")
        }

        try {
            startActivityForResult(intent, REQUEST_RECORD_AUDIO)
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در شروع تشخیص صوت", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_RECORD_AUDIO && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            
            if (!spokenText.isNullOrBlank()) {
                binding.messageInput.setText(spokenText)
            }
        }
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("فایل صوتی", "تصویر", "فایل")
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب نوع فایل")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "آپلود صوت در نسخه بعدی", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "آپلود تصویر در نسخه بعدی", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "آپلود فایل در نسخه بعدی", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showModelSelector() {
        val models = AIModel.values()
        val modelNames = models.map { it.displayName }.toTypedArray()
        val currentIndex = models.indexOf(currentModel)

        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب مدل")
            .setSingleChoiceItems(modelNames, currentIndex) { dialog, which ->
                currentModel = models[which]
                prefsManager.saveSelectedModel(currentModel)
                updateModelDisplay()
                Toast.makeText(this, "مدل به ${currentModel.displayName} تغییر کرد", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun updateModelDisplay() {
        supportActionBar?.subtitle = "مدل: ${currentModel.displayName}"
    }

    private fun refreshAPIKeys() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "در حال به‌روزرسانی...", Toast.LENGTH_SHORT).show()
                
                // TODO: دانلود مجدد کلیدها
                Toast.makeText(this@MainActivity, "به‌روزرسانی موفق", Toast.LENGTH_SHORT).show()
                setupAIClient()
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearChat() {
        MaterialAlertDialogBuilder(this)
            .setTitle("پاک کردن چت")
            .setMessage("آیا مطمئن هستید که می‌خواهید تمام پیام‌ها را پاک کنید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        messageStorage.clearAllMessages()
                    }
                    messages.clear()
                    chatAdapter.notifyDataSetChanged()
                    Toast.makeText(this@MainActivity, "چت پاک شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_model -> {
                showModelSelector()
                true
            }
            R.id.action_refresh_keys -> {
                refreshAPIKeys()
                true
            }
            R.id.action_clear_chat -> {
                clearChat()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
