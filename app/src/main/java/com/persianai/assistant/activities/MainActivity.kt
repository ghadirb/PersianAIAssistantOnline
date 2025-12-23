package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.persianai.assistant.databinding.ActivityMainBinding
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.FinanceManager
import com.persianai.assistant.finance.InstallmentManager
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.*
import com.persianai.assistant.utils.PreferencesManager.ProviderPreference
import com.persianai.assistant.ai.PuterBridge
import com.persianai.assistant.services.AIAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import java.io.File
import com.persianai.assistant.services.VoiceRecordingHelper
import com.persianai.assistant.models.OfflineModelManager

/**
 * صفحه اصلی چت
 */
class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private var audioFilePath: String = ""
    private var isRecording = false
    private var recordingTimer: java.util.Timer? = null
    private var voiceEngineHelperInitialized = true
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private val swipeThreshold = 100f
    private lateinit var voiceHelper: VoiceRecordingHelper
    private lateinit var conversationStorage: com.persianai.assistant.storage.ConversationStorage
    private var currentConversation: com.persianai.assistant.models.Conversation? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var prefsManager: PreferencesManager
    private lateinit var ttsHelper: com.persianai.assistant.utils.TTSHelper
    private lateinit var advancedAssistant: com.persianai.assistant.ai.AdvancedPersianAssistant
    private lateinit var smartReminderManager: SmartReminderManager
    private lateinit var financeManager: FinanceManager
    private lateinit var checkManager: CheckManager
    private lateinit var installmentManager: InstallmentManager
    private var aiClient: AIClient? = null
    private var currentModel: AIModel = AIModel.LLAMA_3_3_70B
    private lateinit var speechRecognizer: SpeechRecognizer

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val NOTIFICATION_PERMISSION_CODE = 1002
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
            ttsHelper = com.persianai.assistant.utils.TTSHelper(this)
            advancedAssistant = com.persianai.assistant.ai.AdvancedPersianAssistant(this)
            smartReminderManager = SmartReminderManager(this)
            financeManager = FinanceManager(this)
            checkManager = CheckManager(this)
            installmentManager = InstallmentManager(this)
            conversationStorage = com.persianai.assistant.storage.ConversationStorage(this)
            
            // Setup Voice Recording Helper
            voiceHelper = VoiceRecordingHelper(this)
            setupVoiceRecording()
            
            // بارگذاری خودکار کلیدهای API
            lifecycleScope.launch {
                try {
                    val result = AutoProvisioningManager.autoProvision(this@MainActivity)
                    if (result.isSuccess) {
                        val keys = result.getOrNull() ?: emptyList()
                        android.util.Log.d("MainActivity", "✅ ${keys.size} کلید API بارگذاری شد")
                        
                        // راه‌اندازی مجدد AIClient
                        setupAIClient()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "خطا در بارگذاری خودکار کلیدها", e)
                }
            }
            
            android.util.Log.d("MainActivity", "Managers initialized")
            
            setupChatUI()
            
            loadMessages()
            android.util.Log.d("MainActivity", "Messages loaded")
            
            setupListeners()
            android.util.Log.d("MainActivity", "Listeners setup")
            
            updateModelDisplay()
            android.util.Log.d("MainActivity", "Model display updated")
            
            updateModeIndicator()
            android.util.Log.d("MainActivity", "Mode indicator updated")

            // Chip actions
            binding.modelIndicator.setOnClickListener {
                showModelSelector()
            }

            binding.modeIndicator.setOnClickListener {
                // cycle: OFFLINE -> HYBRID -> ONLINE
                val next = when (prefsManager.getWorkingMode()) {
                    PreferencesManager.WorkingMode.OFFLINE -> PreferencesManager.WorkingMode.HYBRID
                    PreferencesManager.WorkingMode.HYBRID -> PreferencesManager.WorkingMode.ONLINE
                    PreferencesManager.WorkingMode.ONLINE -> PreferencesManager.WorkingMode.OFFLINE
                }
                prefsManager.setWorkingMode(next)
                updateModeIndicator()
                Toast.makeText(this, "حالت: ${binding.modeIndicator.text}", Toast.LENGTH_SHORT).show()
            }
            
            // نمایش پیام خوش‌آمدگویی در اولین اجرا
            showFirstRunDialogIfNeeded()
            
            // شروع سرویس پس‌زمینه
            startBackgroundService()
            
            // درخواست permission نوتیفیکیشن برای Android 13+
            requestNotificationPermission()
            
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

        // Wire unified VoiceActionButton if present to reuse existing helpers
        // ✅ اصلاح شده: اکنون voiceButton خود VoiceActionButton است (نه MaterialButton)
        try {
            val vab = findViewById<com.persianai.assistant.ui.VoiceActionButton?>(
                resources.getIdentifier("voiceButton", "id", packageName)
            )
            if (vab != null) {
                vab.setListener(object : com.persianai.assistant.ui.VoiceActionButton.Listener {
                    override fun onRecordingStarted() {
                        isRecording = true
                        binding.recordingIndicator.visibility = View.VISIBLE
                    }

                    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                        isRecording = false
                        binding.recordingIndicator.visibility = View.GONE
                        try {
                            transcribeAndSendAudio(audioFile.absolutePath)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error processing recorded file", e)
                        }
                    }

                    override fun onTranscript(text: String) {
                        binding.messageInput.setText(text)
                        sendMessage()
                    }

                    override fun onRecordingError(error: String) {
                        isRecording = false
                        binding.recordingIndicator.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "خطا در ضبط: $error", Toast.LENGTH_SHORT).show()
                    }
                })
                android.util.Log.d("MainActivity", "✅ VoiceActionButton wired successfully")
            } else {
                android.util.Log.w("MainActivity", "⚠️ VoiceActionButton not found")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error wiring VoiceActionButton", e)
        }
    }


    private fun setupListeners() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // ✅ VoiceActionButton مدیریت خود را انجام می‌دهد - دستی setOnTouchListener لازم نیست
        // touch listener برای voiceButton حذف شده است زیرا VoiceActionButton خود را مدیریت می‌کند

        // حذف دکمه attach (قابلیت آپلود فایل فعلاً غیرفعال)
        binding.attachButton.visibility = View.GONE
    }
    
    private fun startBackgroundService() {
        val serviceIntent = Intent(this, AIAssistantService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun setupChatUI() {
        setupRecyclerView()
        setupAIClient()
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
            
            // استفاده از ModelSelector برای انتخاب هوشمند مدل
            val preferred = prefsManager.getSelectedModel()
            
            // چک کردن که آیا مدل انتخابی قابل استفاده است
            currentModel = if (ModelSelector.isModelAvailable(preferred, apiKeys)) {
                preferred
            } else {
                // انتخاب بهترین مدل سبک
                ModelSelector.selectBestModel(apiKeys, preferLightweight = true)
            }
            
            prefsManager.saveSelectedModel(currentModel)
            updateModelDisplay()
            
            android.util.Log.d("MainActivity", "✅ مدل انتخاب شد: ${currentModel.displayName}")
        }
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
        binding.recyclerView.smoothScrollToPosition(messages.size - 1)
        if (message.role == MessageRole.ASSISTANT && !message.isError) {
            ttsHelper.speak(message.content)
        }
    }

    private fun chooseBestModel(apiKeys: List<APIKey>, pref: ProviderPreference): AIModel {
        // استفاده از ModelSelector جدید
        return ModelSelector.selectBestModel(apiKeys, preferLightweight = true)
    }



    private fun checkAudioPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        } else {
            // Use centralized VoiceRecordingHelper (backed by UnifiedVoiceEngine)
            try {
                voiceHelper.startRecording()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to start recording via helper", e)
                startRecording() // fallback to legacy method
            }
        }
    }

    private fun startRecording() {
        // Legacy fallback removed; use `VoiceRecordingHelper` only.
    }



    private fun cancelRecording() {
        if (!isRecording) return
        try {
            voiceHelper.cancelRecording()
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Error cancelling recording via helper", e)
        }
        isRecording = false
        recordingTimer?.cancel()
        // If a legacy file path exists, delete it (defensive)
        try { File(audioFilePath).delete() } catch (_: Exception) {}
    }




    private fun isActionCommand(text: String): Boolean {
        val keywords = listOf("یادآوری", "چک", "قسط", "مسیریابی", "درآمد", "هزینه")
        return keywords.any { text.contains(it) }
    }

    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "لطفاً پیامی وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        
        val mode = prefsManager.getWorkingMode()
        
        // در حالت آفلاین (یا بدون کلاینت آنلاین)، دستورات مالی/یادآوری را مستقیماً با دستیار پیشرفته پردازش کن
        if (isActionCommand(text) && (mode == PreferencesManager.WorkingMode.OFFLINE || aiClient == null)) {
            val response = advancedAssistant.processRequest(text)
            
            addMessage(
                ChatMessage(
                    role = MessageRole.USER,
                    content = text,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            val aiMessage = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = response.text,
                timestamp = System.currentTimeMillis()
            )
            addMessage(aiMessage)
            handleAssistantAction(response.actionType)
            
            binding.messageInput.text?.clear()
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
                val mode = prefsManager.getWorkingMode()
                val isModelDownloaded = prefsManager.isOfflineModelDownloaded()
                
                val response = when (mode) {
                    PreferencesManager.WorkingMode.OFFLINE -> {
                        // حالت آفلاین
                        if (isModelDownloaded) {
                            handleOfflineRequest(text)
                        } else {
                            "⚠️ مدل آفلاین دانلود نشده است.\n\nلطفاً از تنظیمات، مدل را دانلود کنید یا به حالت آنلاین بروید."
                        }
                    }
                    
                    PreferencesManager.WorkingMode.ONLINE -> {
                        // حالت آنلاین
                        if (aiClient == null) {
                            "❌ کلید API تنظیم نشده است.\n\nلطفاً از تنظیمات، کلیدهای API را وارد کنید."
                        } else {
                            handleOnlineRequest(text)
                        }
                    }
                    
                    PreferencesManager.WorkingMode.HYBRID -> {
                        // حالت ترکیبی - اول آفلاین، بعد آنلاین
                        val offlineParser = com.persianai.assistant.ai.OfflineIntentParser(this@MainActivity)
                        
                        if (isModelDownloaded && offlineParser.canHandle(text)) {
                            // دستور ساده - از آفلاین استفاده کن
                            handleOfflineRequest(text)
                        } else if (aiClient != null) {
                            // دستور پیچیده - از آنلاین استفاده کن
                            handleOnlineRequest(text)
                        } else {
                            "⚠️ برای این درخواست نیاز به اتصال آنلاین است ولی کلید API تنظیم نشده.\n\nلطفاً کلیدها را از تنظیمات وارد کنید."
                        }
                    }
                }
                
                val finalMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = response,
                    timestamp = System.currentTimeMillis()
                )
                addMessage(finalMessage)
                
                // ذخیره چت
                saveCurrentConversation()
                
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "❌ خطا: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
                addMessage(errorMessage)
            } finally {
                binding.sendButton.isEnabled = true
            }
        }
    }
    
    private fun handleAssistantAction(action: com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType?) {
        when (action) {
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.OPEN_REMINDERS,
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.ADD_REMINDER -> {
                startActivity(Intent(this, AdvancedRemindersActivity::class.java))
            }
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.OPEN_CHECKS,
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.ADD_CHECK -> {
                startActivity(Intent(this, ChecksManagementActivity::class.java))
            }
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.OPEN_INSTALLMENTS,
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.ADD_INSTALLMENT -> {
                startActivity(Intent(this, InstallmentsManagementActivity::class.java))
            }
            com.persianai.assistant.ai.AdvancedPersianAssistant.ActionType.OPEN_TRAVEL -> {
                startActivity(Intent(this, NavigationActivity::class.java))
            }
            else -> {}
        }
    }
    
    private fun showFirstRunDialogIfNeeded() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        
        if (isFirstRun && !prefsManager.hasAPIKeys()) {
            prefs.edit().putBoolean("is_first_run", false).apply()
            downloadAndDecryptKeys("12345")
        }
    }
    
    private fun showPasswordDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.hint = "رمز عبور"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                          android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 2
        params.rightMargin = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 2
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle("ورود رمز عبور")
            .setMessage("لطفاً رمز عبور کلیدهای API را وارد کنید (پیش‌فرض: 12345)")
            .setView(container)
            .setPositiveButton("تأیید") { _, _ ->
                val password = input.text.toString()
                if (password.isNotEmpty()) {
                    downloadAndDecryptKeys(password)
                } else {
                    Toast.makeText(this, "رمز عبور نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .setCancelable(false)
            .show()
    }
    
    private fun downloadAndDecryptKeys(password: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "در حال دانلود...", Toast.LENGTH_SHORT).show()
                
                // دانلود فایل رمزشده از Google Drive
                val encryptedData = try {
                    withContext(Dispatchers.IO) {
                        DriveHelper.downloadEncryptedKeys()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "خطا در دانلود: ${e.message}\nلطفاً اتصال اینترنت را بررسی کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                
                Toast.makeText(this@MainActivity, "در حال رمزگشایی...", Toast.LENGTH_SHORT).show()
                
                // رمزگشایی
                val decryptedData = withContext(Dispatchers.IO) {
                    EncryptionHelper.decrypt(encryptedData, password)
                }
                
                // پردازش کلیدها
                val apiKeys = parseAPIKeys(decryptedData)
                
                if (apiKeys.isEmpty()) {
                    throw Exception("هیچ کلید معتبری یافت نشد")
                }
                
                // ذخیره کلیدها
                prefsManager.saveAPIKeys(apiKeys)
                
                Toast.makeText(
                    this@MainActivity,
                    "کلیدها با موفقیت بارگذاری شدند (${apiKeys.size} کلید)",
                    Toast.LENGTH_LONG
                ).show()
                
                // راه‌اندازی مجدد AI Client
                setupAIClient()
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error downloading/decrypting keys", e)
                
                Toast.makeText(
                    this@MainActivity,
                    "خطا: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun parseAPIKeys(data: String): List<com.persianai.assistant.models.APIKey> {
        val keys = mutableListOf<com.persianai.assistant.models.APIKey>()
        
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            
            // فرمت: provider:key یا فقط key
            val parts = trimmed.split(":", limit = 2)
            
            if (parts.size == 2) {
                val provider = when (parts[0].lowercase()) {
                    "openai" -> com.persianai.assistant.models.AIProvider.OPENAI
                    "anthropic", "claude" -> com.persianai.assistant.models.AIProvider.ANTHROPIC
                    "openrouter" -> com.persianai.assistant.models.AIProvider.OPENROUTER
                    else -> null
                }
                
                if (provider != null) {
                    keys.add(com.persianai.assistant.models.APIKey(provider, parts[1].trim(), true))
                }
            } else if (parts.size == 1 && trimmed.startsWith("sk-")) {
                // تشخیص نوع کلید از روی prefix
                val provider = when {
                    trimmed.startsWith("sk-proj-") -> com.persianai.assistant.models.AIProvider.OPENAI
                    trimmed.startsWith("sk-or-") -> com.persianai.assistant.models.AIProvider.OPENROUTER
                    trimmed.length == 51 && trimmed.startsWith("sk-") -> com.persianai.assistant.models.AIProvider.ANTHROPIC
                    else -> com.persianai.assistant.models.AIProvider.OPENAI
                }
                keys.add(com.persianai.assistant.models.APIKey(provider, trimmed, true))
            }
        }
        
        return keys
    }

    private suspend fun handleOfflineRequest(text: String): String = withContext(Dispatchers.IO) {
        // 1) اگر مدل واقعی GGUF وجود دارد، از TinyLlama استفاده کن
        val modelPath = findOfflineModelPath()
        if (modelPath != null) {
            val prompt = buildString {
                append("شما یک دستیار فارسی هستید. پاسخ کوتاه و مستقیم بده.\n")
                append("کاربر: ").append(text).append("\nدستیار:")
            }
            return@withContext try {
                android.util.Log.d("MainActivity", "offline llm using model=$modelPath")
                val out = com.persianai.assistant.offline.LocalLlamaRunner.infer(modelPath, prompt, maxTokens = 128)
                if (!out.isNullOrBlank()) {
                    "🟢 پاسخ آفلاین (TinyLlama):\n$out"
                } else {
                    android.util.Log.w("MainActivity", "offline llm returned empty; fallback to parser")
                    offlineParserFallback(text)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "offline llm failed: ${e.message}")
                offlineParserFallback(text)
            }
        }

        // 2) در غیر این صورت، fallback قبلی (پارسر آفلاین)
        return@withContext offlineParserFallback(text)
    }

    private suspend fun offlineParserFallback(text: String): String {
        val parser = com.persianai.assistant.ai.OfflineIntentParser(this@MainActivity)
        val intentJson = parser.parse(text)
        return processAIResponse(intentJson)
    }

    private fun findOfflineModelPath(): String? {
        return try {
            val manager = OfflineModelManager(this)
            val list = manager.getDownloadedModels()
            list.firstOrNull { it.first.name.contains("TinyLlama", ignoreCase = true) }?.second
                ?: list.firstOrNull()?.second
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "findOfflineModelPath failed: ${e.message}")
            null
        }
    }
    
    private suspend fun handleOnlineRequest(text: String): String = withContext(Dispatchers.IO) {
        val enhancedPrompt = """
            شما یک دستیار هوشمند فارسی هستید که باید تا حد امکان پاسخ‌های خود را به صورت اکشن‌های ساختارمند JSON برگردانید تا برنامه بتواند آن‌ها را اجرا کند.

            قوانین کلی:
            - اگر می‌توان عملی روی گوشی انجام داد (یادآوری، مسیریابی، ارسال پیام، باز کردن برنامه، ثبت تراکنش مالی و ...)، حتماً یک آبجکت JSON با فیلد "action" برگردان.
            - اگر هیچ اکشن مستقیمی وجود نداشت (مثلاً فقط یک سوال عمومی است)، فقط متن معمولی فارسی برگردان.

            اکشن‌های پشتیبانی‌شده:

            1) تنظیم یا مدیریت یادآوری‌ها
            - برای ساخت یادآوری جدید:
              {"action":"add_reminder","time":"HH:mm","message":"متن یادآوری","repeat":"none" یا "daily"}
              مثال: {"action":"add_reminder","time":"09:00","message":"قرص بخورم","repeat":"daily"}

            - برای نمایش فهرست یادآوری‌ها:
              {"action":"list_reminders"}

            2) مسیریابی
              {"action":"navigation","destination":"آدرس یا نام مکان","voice":true/false}

            3) ارسال پیام در پیام‌رسان‌ها
              {"action":"send_telegram","phone":"شماره یا خالی","message":"متن"}
              {"action":"send_whatsapp","phone":"شماره یا خالی","message":"متن"}
              {"action":"send_sms","phone":"شماره یا خالی","message":"متن"}
              {"action":"send_rubika","message":"متن"}
              {"action":"send_eitaa","message":"متن"}

            4) باز کردن برنامه‌ها
              {"action":"open_app","app_name":"نام برنامه به فارسی یا انگلیسی"}

            5) حسابداری و مدیریت مالی
            - ثبت درآمد:
              {"action":"add_income","amount":مبلغ_به_تومان,"category":"دسته‌بندی اختیاری","description":"توضیح"}
              مثال: {"action":"add_income","amount":500000,"category":"حقوق","description":"حقوق دی ماه"}

            - ثبت هزینه:
              {"action":"add_expense","amount":مبلغ_به_تومان,"category":"دسته‌بندی اختیاری","description":"توضیح"}
              مثال: {"action":"add_expense","amount":200000,"category":"خوراک","description":"نهار"}

            - ثبت چک جدید:
              {"action":"add_check","amount":مبلغ_به_تومان,"check_number":"شماره چک","issuer":"صادرکننده","recipient":"دریافت‌کننده","bank_name":"بانک","account_number":"شماره حساب","due_date":"YYYY/MM/DD"}

            - ثبت قسط/وام جدید:
              {"action":"add_installment","title":"مثلاً قسط ماشین","total_amount":مبلغ_کل_تومان,"monthly_amount":مبلغ_هر_قسط_تومان (اختیاری),"months":تعداد_اقساط,"payment_day":روز_ماه (1-31),"recipient":"دریافت‌کننده","description":"توضیح"}

            - گزارش مالی کلی:
              {"action":"finance_report"}

            نکات مهم:
            - حتماً JSON را به صورت یک آبجکت واحد و معتبر برگردان (با { و }).
            - از متن اضافه قبل و بعد از JSON تا حد امکان پرهیز کن، مگر این که لازم باشد توضیحی کوتاه بدهی.
            - مقدار "amount" همیشه بر حسب تومان باشد (اگر کاربر گفت میلیون یا هزار، خودت تبدیل کن).
            - اگر نیاز به سوال پیگیری داری (مثلاً مبلغ، تاریخ سررسید یا تعداد اقساط مشخص نیست)، به صورت متن عادی فارسی بپرس.

            حالا بر اساس این قوانین، پیام کاربر را تحلیل کن و یا یک JSON اکشن مناسب، و یا یک پاسخ متنی معمولی فارسی تولید کن.

            پیام کاربر: $text
        """.trimIndent()

        val providerPref = prefsManager.getProviderPreference()
        if (providerPref == ProviderPreference.AUTO || providerPref == ProviderPreference.SMART_ROUTE) {
            try {
                val puterReply = PuterBridge.chat(text, messages)
                if (!puterReply.isNullOrBlank()) {
                    return@withContext processAIResponse(puterReply)
                }
            } catch (e: Exception) {
                // ساکت: مستقیماً fallback
            }
        }

        val response = aiClient!!.sendMessage(currentModel, messages, enhancedPrompt)
        return@withContext processAIResponse(response.content)
    }

    private suspend fun processAIResponse(response: String): String {
        return withContext(Dispatchers.Main) {
            android.util.Log.d("MainActivity", "AI Response: $response")
            
            // اگه مدل refuse کرد، خودمون برنامه رو باز می‌کنیم
            val userMsg = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
            if (response.contains("متاسفانه") || response.contains("نمی‌توانم") ||
                response.contains("متاسفم") || response.toLowerCase(java.util.Locale.ROOT).contains("i cannot") ||
                response.contains("دسترسی ندارم") || response.contains("امکان")) {
                return@withContext when {
                    userMsg.contains("ایتا") || userMsg.contains("eitaa") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "ایتا")
                        "✅ ایتا باز شد"
                    }
                    userMsg.contains("روبیکا") || userMsg.contains("rubika") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "روبیکا")
                        "✅ روبیکا باز شد"
                    }
                    userMsg.contains("واتساپ") || userMsg.contains("whatsapp") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "واتساپ")
                        "✅ واتساپ باز شد"
                    }
                    userMsg.contains("نشان") || userMsg.contains("neshan") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "نشان")
                        "✅ نشان باز شد"
                    }
                    userMsg.contains("گپ") || userMsg.contains("gap") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "گپ")
                        "✅ گپ باز شد"
                    }
                    userMsg.contains("اینستاگرام") || userMsg.contains("instagram") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "اینستاگرام")
                        "✅ اینستاگرام باز شد"
                    }
                    userMsg.contains("یوتیوب") || userMsg.contains("youtube") -> {
                        SystemIntegrationHelper.openApp(this@MainActivity, "یوتیوب")
                        "✅ یوتیوب باز شد"
                    }
                    userMsg.contains("پیام‌نگار") || userMsg.contains("پیامک") || userMsg.contains("sms") -> {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("sms:")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        "✅ پیام‌نگار باز شد"
                    }
                    else -> response // Return original AI response if no app keyword is matched
                }
            }
            
            when {
                // یادآوری
                response.contains("REMINDER:") -> {
                    try {
                        val jsonStr = response.substringAfter("REMINDER:").substringBefore("\n").trim()
                        val json = org.json.JSONObject(jsonStr)
                        val time = json.getString("time")
                        val message = json.getString("message")
                        val useAlarm = json.optBoolean("alarm", false)
                        val repeat = json.optString("repeat", "none")
                        
                        // استخراج ساعت و دقیقه
                        val parts = time.split(":")
                        val hour = parts[0].toInt()
                        val minute = parts[1].toInt()
                        
                        // محاسبه repeatInterval
                        val repeatInterval = when (repeat.lowercase()) {
                            "daily", "روزانه", "هر روز" -> android.app.AlarmManager.INTERVAL_DAY
                            else -> 0L
                        }
                        
                        SystemIntegrationHelper.setReminder(
                            this@MainActivity, 
                            message, 
                            hour, 
                            minute,
                            useAlarm,
                            repeatInterval
                        )
                        
                        // ذخیره در لیست یادآوری‌ها
                        RemindersActivity.addReminder(this@MainActivity, time, message)
                        
                        val alarmType = if (useAlarm) "🔔 آلارم" else "📱 نوتیفیکیشن"
                        val repeatText = if (repeatInterval > 0) "🔁 روزانه" else "یکبار"
                        
                        "✅ یادآوری تنظیم شد:\n⏰ ساعت $time\n📝 $message\n$alarmType | $repeatText\n\n💡 برای مشاهده لیست یادآوری‌ها، از منو استفاده کنید."
                    } catch (e: Exception) {
                        response.replace("REMINDER:", "")
                    }
                }
                
                // مسیریابی
                response.contains("NAVIGATION:") -> {
                    try {
                        val jsonStr = response.substringAfter("NAVIGATION:").substringBefore("\n").trim()
                        val json = org.json.JSONObject(jsonStr)
                        val destination = json.getString("destination")
                        val withVoice = json.optBoolean("voice", false)
                        
                        SystemIntegrationHelper.openNavigation(this@MainActivity, destination, withVoice)
                        
                        if (withVoice) {
                            "🗺️ در حال باز کردن مسیریابی فارسی به:\n📍 $destination\n🔊 با راهنمای صوتی فارسی"
                        } else {
                            "🗺️ در حال باز کردن مسیریابی به:\n📍 $destination"
                        }
                    } catch (e: Exception) {
                        response.replace("NAVIGATION:", "")
                    }
                }
                
                // پردازش JSON actions
                response.contains("\"action\"") && response.contains("{") -> {
                    try {
                        // استخراج JSON از پاسخ
                        val startIndex = response.indexOf("{")
                        val endIndex = response.indexOf("}", startIndex) + 1
                        val jsonStr = response.substring(startIndex, endIndex)
                        
                        android.util.Log.d("MainActivity", "JSON extracted: $jsonStr")
                        
                        val json = org.json.JSONObject(jsonStr)
                        val action = json.getString("action")
                        
                        when (action) {
                            "add_reminder" -> {
                                val time = json.optString("time", "")
                                val message = json.optString("message", "")
                                val repeatRaw = json.optString("repeat", "none")

                                if (time.isBlank() || message.isBlank()) {
                                    "⚠️ برای تنظیم یادآوری، زمان (HH:mm) و متن یادآوری لازم است."
                                } else {
                                    val parts = time.split(":")
                                    val hour = parts.getOrNull(0)?.toIntOrNull()
                                    val minute = parts.getOrNull(1)?.toIntOrNull()

                                    if (hour == null || minute == null) {
                                        "⚠️ فرمت زمان نامعتبر است. لطفاً به صورت HH:mm (مثلاً 09:30) استفاده کنید."
                                    } else {
                                        val calendar = java.util.Calendar.getInstance()
                                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                                        calendar.set(java.util.Calendar.MINUTE, minute)
                                        calendar.set(java.util.Calendar.SECOND, 0)
                                        calendar.set(java.util.Calendar.MILLISECOND, 0)

                                        // اگر زمان گذشته بود، برای فردا تنظیم کن
                                        if (calendar.timeInMillis <= System.currentTimeMillis()) {
                                            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                                        }

                                        val triggerTime = calendar.timeInMillis

                                        val title = message.take(40)
                                        val description = if (message.length > 40) message else ""

                                        val isDaily = repeatRaw.equals("daily", ignoreCase = true) ||
                                                repeatRaw == "روزانه" || repeatRaw == "هر روز"

                                        val createdReminder = if (isDaily) {
                                            smartReminderManager.createRecurringReminder(
                                                title = title,
                                                description = description,
                                                firstTriggerTime = triggerTime,
                                                repeatPattern = SmartReminderManager.RepeatPattern.DAILY
                                            )
                                        } else {
                                            smartReminderManager.createSimpleReminder(
                                                title = title,
                                                description = description,
                                                triggerTime = triggerTime
                                            )
                                        }

                                        val readableTime = java.text.SimpleDateFormat(
                                            "HH:mm",
                                            java.util.Locale.getDefault()
                                        ).format(java.util.Date(createdReminder.triggerTime))

                                        val repeatText = if (isDaily) "🔁 هر روز" else "یکبار"

                                        "✅ یادآوری تنظیم شد:\n" +
                                                "⏰ $readableTime\n" +
                                                "📝 $message\n" +
                                                "📌 $repeatText"
                                    }
                                }
                            }
                            "list_reminders" -> {
                                val activeReminders = smartReminderManager.getActiveReminders()
                                    .sortedBy { it.triggerTime }

                                if (activeReminders.isEmpty()) {
                                    "⏰ شما هیچ یادآوری فعالی ندارید."
                                } else {
                                    val timeFormat = java.text.SimpleDateFormat(
                                        "HH:mm",
                                        java.util.Locale.getDefault()
                                    )

                                    val builder = StringBuilder()
                                    builder.appendLine("⏰ یادآوری‌های فعال شما:")
                                    activeReminders.take(5).forEach { reminder ->
                                        val timeStr = timeFormat.format(java.util.Date(reminder.triggerTime))
                                        builder.appendLine("• ${reminder.title} - ساعت $timeStr")
                                    }
                                    if (activeReminders.size > 5) {
                                        builder.appendLine("... و ${activeReminders.size - 5} مورد دیگر.")
                                    }
                                    builder.toString().trim()
                                }
                            }
                            "navigation" -> {
                                val destination = json.optString("destination", "")
                                val withVoice = json.optBoolean("voice", false)

                                if (destination.isBlank()) {
                                    "⚠️ مقصد مسیریابی مشخص نیست."
                                } else {
                                    SystemIntegrationHelper.openNavigation(this@MainActivity, destination, withVoice)
                                    if (withVoice) {
                                        "🗺️ در حال باز کردن مسیریابی فارسی به:\n📍 $destination\n🔊 با راهنمای صوتی فارسی"
                                    } else {
                                        "🗺️ در حال باز کردن مسیریابی به:\n📍 $destination"
                                    }
                                }
                            }
                            "add_income" -> {
                                val amount = json.optDouble("amount", Double.NaN)
                                if (amount.isNaN() || amount <= 0.0) {
                                    "⚠️ مبلغ درآمد نامعتبر است."
                                } else {
                                    val category = json.optString("category", "سایر")
                                    val description = json.optString("description", "درآمد ثبت‌شده از چت")

                                    financeManager.addTransaction(amount, "income", category, description)

                                    val formatted = String.format("%,.0f", amount)
                                    "✅ درآمد $formatted تومان ثبت شد\nدسته‌بندی: $category"
                                }
                            }
                            "add_expense" -> {
                                val amount = json.optDouble("amount", Double.NaN)
                                if (amount.isNaN() || amount <= 0.0) {
                                    "⚠️ مبلغ هزینه نامعتبر است."
                                } else {
                                    val category = json.optString("category", "سایر")
                                    val description = json.optString("description", "هزینه ثبت‌شده از چت")

                                    financeManager.addTransaction(amount, "expense", category, description)

                                    val formatted = String.format("%,.0f", amount)
                                    "✅ هزینه $formatted تومان ثبت شد\nدسته‌بندی: $category"
                                }
                            }
                            "add_check" -> {
                                val amount = json.optDouble("amount", Double.NaN)
                                val checkNumber = json.optString("check_number", "").trim()
                                val issuer = json.optString("issuer", "نامشخص").trim()
                                val recipient = json.optString("recipient", "نامشخص").trim()
                                val bankName = json.optString("bank_name", "بانک نامشخص").trim()
                                val accountNumber = json.optString("account_number", "-").trim()
                                val description = json.optString("description", "چک ثبت‌شده از چت").trim()
                                val dueDateStr = json.optString("due_date", "").trim()

                                if (amount.isNaN() || amount <= 0.0) {
                                    "⚠️ مبلغ چک نامعتبر است."
                                } else if (checkNumber.isEmpty() || dueDateStr.isEmpty()) {
                                    "⚠️ برای ثبت چک، شماره چک و تاریخ سررسید (YYYY/MM/DD) لازم است."
                                } else {
                                    val formatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                                    formatter.isLenient = false
                                    val dueDate = try {
                                        formatter.parse(dueDateStr)?.time
                                    } catch (e: Exception) {
                                        null
                                    }

                                    if (dueDate == null) {
                                        "⚠️ فرمت تاریخ سررسید نامعتبر است. از قالب YYYY/MM/DD استفاده کنید."
                                    } else {
                                        val issueDate = System.currentTimeMillis()
                                        checkManager.addCheck(
                                            checkNumber = checkNumber,
                                            amount = amount,
                                            issuer = issuer,
                                            recipient = recipient,
                                            issueDate = issueDate,
                                            dueDate = dueDate,
                                            bankName = bankName,
                                            accountNumber = accountNumber,
                                            description = description
                                        )

                                        val formattedAmount = String.format("%,.0f", amount)
                                        val dueDateReadable = formatter.format(java.util.Date(dueDate))

                                        "✅ چک جدید ثبت شد:\n" +
                                                "شماره: $checkNumber\n" +
                                                "مبلغ: $formattedAmount تومان\n" +
                                                "سررسید: $dueDateReadable\n" +
                                                "گیرنده: $recipient"
                                    }
                                }
                            }
                            "add_installment" -> {
                                val title = json.optString("title", "قسط جدید").trim()
                                val totalAmount = json.optDouble("total_amount", Double.NaN)
                                val months = json.optInt("months", 0)
                                val paymentDay = json.optInt("payment_day", 0)
                                val monthlyAmountJson = if (json.has("monthly_amount")) json.optDouble("monthly_amount", Double.NaN) else Double.NaN
                                val recipient = json.optString("recipient", "نامشخص").trim()
                                val description = json.optString("description", "قسط ثبت‌شده از چت").trim()

                                if (totalAmount.isNaN() || totalAmount <= 0.0 || months <= 0) {
                                    "⚠️ برای ثبت قسط، مبلغ کل و تعداد اقساط باید معتبر باشند."
                                } else {
                                    val baseMonthly = if (!monthlyAmountJson.isNaN() && monthlyAmountJson > 0.0) {
                                        monthlyAmountJson
                                    } else {
                                        (totalAmount / months).coerceAtLeast(0.0)
                                    }

                                    if (baseMonthly <= 0.0) {
                                        "⚠️ مبلغ هر قسط نامعتبر است."
                                    } else {
                                        val calendar = java.util.Calendar.getInstance()
                                        val startDate = calendar.timeInMillis
                                        val dayOfMonth = if (paymentDay in 1..31) paymentDay else calendar.get(java.util.Calendar.DAY_OF_MONTH)

                                        installmentManager.addInstallment(
                                            title = title,
                                            totalAmount = totalAmount,
                                            installmentAmount = baseMonthly,
                                            totalInstallments = months,
                                            startDate = startDate,
                                            paymentDay = dayOfMonth,
                                            recipient = recipient,
                                            description = description
                                        )

                                        val totalFormatted = String.format("%,.0f", totalAmount)
                                        val monthlyFormatted = String.format("%,.0f", baseMonthly)

                                        "✅ قسط جدید ثبت شد:\n" +
                                                "عنوان: $title\n" +
                                                "مبلغ کل: $totalFormatted تومان\n" +
                                                "هر قسط: $monthlyFormatted تومان به مدت $months ماه"
                                    }
                                }
                            }
                            "finance_report" -> {
                                val balance = financeManager.getBalance()
                                val calendar = java.util.Calendar.getInstance()
                                val year = calendar.get(java.util.Calendar.YEAR)
                                val month = calendar.get(java.util.Calendar.MONTH) + 1
                                val (income, expense) = financeManager.getMonthlyReport(year, month)

                                val checksTotal = checkManager.getTotalPendingAmount()
                                val installmentsTotal = installmentManager.getTotalRemainingAmount()

                                val net = income - expense
                                val netWorth = balance - checksTotal - installmentsTotal

                                val fmt = { v: Double -> String.format("%,.0f", v) }

                                buildString {
                                    appendLine("💰 گزارش مالی شما:")
                                    appendLine("📊 موجودی کل: ${fmt(balance)} تومان")
                                    appendLine("📈 درآمد این ماه: ${fmt(income)} تومان")
                                    appendLine("📉 هزینه این ماه: ${fmt(expense)} تومان")
                                    appendLine("💵 سود/زیان این ماه: ${fmt(net)} تومان")

                                    appendLine("\n💼 تعهدات:")
                                    appendLine("📋 چک‌های در انتظار: ${fmt(checksTotal)} تومان")
                                    appendLine("💳 اقساط باقیمانده: ${fmt(installmentsTotal)} تومان")

                                    appendLine("\n💎 خالص دارایی (تقریبی): ${fmt(netWorth)} تومان")

                                    if (netWorth < 0) {
                                        appendLine("\n⚠️ توجه: شما در مجموع بدهی دارید.")
                                    } else {
                                        appendLine("\n✅ وضعیت کلی دارایی شما مثبت است.")
                                    }
                                }.trim()
                            }
                            "send_telegram" -> {
                                val phone = json.optString("phone", "UNKNOWN")
                                val message = json.getString("message")
                                
                                android.util.Log.d("MainActivity", "Opening Telegram with: phone=$phone, message=$message")
                                val success = SystemIntegrationHelper.sendTelegram(this@MainActivity, phone, message)
                                android.util.Log.d("MainActivity", "Telegram open result: $success")
                                
                                if (success) {
                                    if (phone == "UNKNOWN" || phone.isEmpty()) {
                                        "✅ تلگرام باز شد\n💬 پیام: $message\n\nحالا می‌تونی مخاطب رو انتخاب کنی"
                                    } else {
                                        "✅ تلگرام باز شد\n💬 پیام: $message\n📞 به: $phone"
                                    }
                                } else {
                                    "❌ خطا در باز کردن تلگرام. آیا نصب است؟"
                                }
                            }
                            "send_whatsapp" -> {
                                val phone = json.optString("phone", "UNKNOWN")
                                val message = json.getString("message")
                                
                                SystemIntegrationHelper.sendWhatsApp(this@MainActivity, phone, message)
                                
                                if (phone == "UNKNOWN" || phone.isEmpty()) {
                                    "✅ واتساپ باز شد\n💬 پیام: $message\n\nحالا می‌تونی مخاطب رو انتخاب کنی"
                                } else {
                                    "✅ واتساپ باز شد\n💬 پیام: $message\n📞 به: $phone"
                                }
                            }
                            "send_sms" -> {
                                val phone = json.optString("phone", "UNKNOWN")
                                val message = json.getString("message")
                                
                                SystemIntegrationHelper.sendSMS(this@MainActivity, phone, message)
                                
                                if (phone == "UNKNOWN" || phone.isEmpty()) {
                                    "✅ پیام‌نگار باز شد\n💬 پیام: $message\n\nحالا می‌تونی شماره رو وارد کنی"
                                } else {
                                    "✅ پیام‌نگار باز شد\n💬 پیام: $message\n📞 به: $phone"
                                }
                            }
                            "send_rubika" -> {
                                val message = json.optString("message", "")
                                if (message.isNotEmpty()) {
                                    SystemIntegrationHelper.openAppWithMessage(this@MainActivity, "روبیکا", message)
                                    "✅ روبیکا باز شد\n💬 پیام کپی شد - Paste کنید"
                                } else {
                                    SystemIntegrationHelper.openApp(this@MainActivity, "روبیکا")
                                    "✅ روبیکا باز شد"
                                }
                            }
                            "send_eitaa" -> {
                                val message = json.optString("message", "")
                                if (message.isNotEmpty()) {
                                    SystemIntegrationHelper.openAppWithMessage(this@MainActivity, "ایتا", message)
                                    "✅ ایتا باز شد\n💬 پیام کپی شد - Paste کنید"
                                } else {
                                    SystemIntegrationHelper.openApp(this@MainActivity, "ایتا")
                                    "✅ ایتا باز شد"
                                }
                            }
                            "open_app" -> {
                                val appName = json.getString("app_name")
                                val success = SystemIntegrationHelper.openApp(this@MainActivity, appName)
                                if (success) {
                                    "✅ برنامه $appName باز شد"
                                } else {
                                    "⚠️ برنامه $appName در گوشی شما یافت نشد"
                                }
                            }
                            else -> {
                                android.util.Log.w("MainActivity", "Unknown action: $action")
                                response
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error processing JSON action", e)
                        android.util.Log.e("MainActivity", "Response was: $response")
                        response
                    }
                }
                
                else -> response
            }
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            // بررسی اینکه آیا چت قبلی وجود دارد
            val conversationId = conversationStorage.getCurrentConversationId()
            
            if (conversationId != null) {
                // بارگذاری چت قبلی
                currentConversation = conversationStorage.getConversation(conversationId)
                currentConversation?.messages?.let {
                    messages.addAll(it)
                    chatAdapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) {
                        binding.recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            } else {
                // شروع چت جدید
                startNewConversation()
            }
        }
    }
    
    private fun startNewConversation() {
        currentConversation = com.persianai.assistant.models.Conversation()
        conversationStorage.setCurrentConversationId(currentConversation!!.id)
        messages.clear()
        chatAdapter.notifyDataSetChanged()
    }
    
    private fun saveCurrentConversation() {
        lifecycleScope.launch {
            currentConversation?.let { conversation ->
                conversation.messages.clear()
                conversation.messages.addAll(messages)
                
                // تولید عنوان خودکار اگر هنوز "چت جدید" است
                if (conversation.title == "چت جدید" && messages.isNotEmpty()) {
                    conversation.title = conversation.generateTitle()
                }
                
                conversationStorage.saveConversation(conversation)
            }
        }
    }

    private fun checkAudioPermissionAndStartSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        } else {
            startSpeechRecognition()
        }
    }

    private fun startSpeechRecognition() {
        startSpeechToText()
    }

    private fun stopRecordingAndProcess() {
        if (!isRecording) return

        try {
            recordingTimer?.cancel()
            // Prefer helper stop (will call processAudioFile via listener)
            voiceHelper.stopRecording()

            // Hide UI indicator
            binding.recordingIndicator.visibility = android.view.View.GONE
        } catch (e: Exception) {
            // Fallback: attempt to get file from helper and transcribe
            try {
                val fallback = voiceHelper.getRecordingFile()
                binding.recordingIndicator.visibility = android.view.View.GONE
                if (fallback != null) {
                    Toast.makeText(this, "🎤 در حال تبدیل صوت به متن...", Toast.LENGTH_LONG).show()
                    transcribeAndSendAudio(fallback.absolutePath)
                } else {
                    Toast.makeText(this, "خطا در پایان ضبط، فایل صوتی پیدا نشد", Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Toast.makeText(this, "خطا در پایان ضبط: ${ex.message}", Toast.LENGTH_SHORT).show()
                android.util.Log.e("MainActivity", "Stop recording error", ex)
            }
        }
    }
    
    private fun transcribeAndSendAudio(providedPath: String? = null) {
        val filePath = providedPath ?: voiceHelper.getRecordingFile()?.absolutePath ?: audioFilePath
        if (filePath.isEmpty()) return

        lifecycleScope.launch {
            try {
                val mode = prefsManager.getWorkingMode()
                if (mode == PreferencesManager.WorkingMode.OFFLINE) {
                    Toast.makeText(
                        this@MainActivity,
                        "🎙️ در حالت آفلاین، تبدیل صوت به متن آنلاین غیرفعال است.\nاز تشخیص صوت داخلی استفاده می...",
                        Toast.LENGTH_LONG
                    ).show()
                    checkAudioPermissionAndStartSpeechRecognition()
                    return@launch
                }

                // تبدیل صوت به متن با Whisper
                val transcribedText = aiClient?.transcribeAudio(filePath)

                if (transcribedText.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, "⚠️ متنی شناسایی نشد", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                android.util.Log.d("MainActivity", "Whisper transcribed: $transcribedText")

                // نمایش متن در input
                binding.messageInput.setText(transcribedText)
                Toast.makeText(this@MainActivity, "✅ صوت به متن تبدیل شد", Toast.LENGTH_SHORT).show()

                // ارسال خودکار
                sendMessage()

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Transcription error", e)
                Toast.makeText(this@MainActivity, "❌ خطا در تبدیل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.messageInput.setText("🎤 پیام صوتی")
            Toast.makeText(this, "⚠️ تشخیص صوت در دستگاه شما پشتیبانی نمی‌شود", Toast.LENGTH_SHORT).show()
            return
        }
        
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("MainActivity", "Speech recognition ready")
            }
            
            override fun onBeginningOfSpeech() {
                android.util.Log.d("MainActivity", "Speech started")
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                android.util.Log.d("MainActivity", "Speech ended")
            }
            
            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "خطای صوتی"
                    SpeechRecognizer.ERROR_CLIENT -> "خطای کلاینت"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "عدم دسترسی"
                    SpeechRecognizer.ERROR_NETWORK -> "خطای شبکه"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "timeout شبکه"
                    SpeechRecognizer.ERROR_NO_MATCH -> "صدا شناسایی نشد"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "سیستم مشغول"
                    SpeechRecognizer.ERROR_SERVER -> "خطای سرور"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout صدا"
                    else -> "خطای ناشناخته"
                }
                android.util.Log.e("MainActivity", "Speech recognition error: $errorMsg")
                binding.messageInput.setText("🎤 پیام صوتی (خطا در تشخیص)")
                Toast.makeText(this@MainActivity, "⚠️ $errorMsg - لطفاً دستی بنویسید", Toast.LENGTH_SHORT).show()
                speechRecognizer.destroy()
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    binding.messageInput.setText(recognizedText)
                    android.util.Log.d("MainActivity", "Recognized text: $recognizedText")
                    Toast.makeText(this@MainActivity, "✅ متن شناسایی شد", Toast.LENGTH_SHORT).show()
                } else {
                    binding.messageInput.setText("🎤 پیام صوتی")
                }
                speechRecognizer.destroy()
            }
            
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        speechRecognizer.startListening(recognizerIntent)
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
        supportActionBar?.apply {
            title = "دستیار هوش مصنوعی"
            subtitle = "${currentModel.displayName}"
        }
        val providerLabel = when (currentModel.provider) {
            com.persianai.assistant.models.AIProvider.OPENROUTER -> "مسیر هوشمند"
            com.persianai.assistant.models.AIProvider.OPENAI -> "OpenAI"
            com.persianai.assistant.models.AIProvider.ANTHROPIC -> "Claude"
            else -> currentModel.provider.name
        }
        binding.modelIndicator.text = "مدل: ${currentModel.displayName} • $providerLabel"
    }
    
    private fun updateModeIndicator() {
        val mode = prefsManager.getWorkingMode()
        val isModelDownloaded = prefsManager.isOfflineModelDownloaded()
        
        val (text, color) = when (mode) {
            PreferencesManager.WorkingMode.ONLINE -> {
                "🌐 آنلاین" to "#E3F2FD"
            }
            PreferencesManager.WorkingMode.OFFLINE -> {
                if (isModelDownloaded) {
                    "📱 آفلاین" to "#F1F8E9"
                } else {
                    "⚠️ آفلاین (مدل ندارد)" to "#FFEBEE"
                }
            }
            PreferencesManager.WorkingMode.HYBRID -> {
                "⚡ ترکیبی" to "#FFF3E0"
            }
        }
        
        binding.modeIndicator.text = text
        binding.modeIndicator.setChipBackgroundColorResource(android.R.color.transparent)
        binding.modeIndicator.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(color)
        )
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
                    currentConversation?.let {
                        withContext(Dispatchers.IO) {
                            conversationStorage.deleteConversation(it.id)
                            conversationStorage.clearCurrentConversationId()
                        }
                    }
                    startNewConversation()
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
            R.id.action_new_chat -> {
                // ذخیره چت فعلی
                if (messages.isNotEmpty()) {
                    saveCurrentConversation()
                }
                // شروع چت جدید
                conversationStorage.clearCurrentConversationId()
                startNewConversation()
                Toast.makeText(this, "چت جدید شروع شد", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_conversations -> {
                // ذخیره چت فعلی
                if (messages.isNotEmpty()) {
                    saveCurrentConversation()
                }
                startActivity(Intent(this, ConversationsActivity::class.java))
                true
            }
            R.id.action_select_model -> {
                showModelSelector()
                true
            }
            R.id.action_clear_chat -> {
                clearChat()
                true
            }
            R.id.action_refresh_keys -> {
                refreshAPIKeys()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // ===== Voice Recording Setup =====
    
    private fun setupVoiceRecording() {
        voiceHelper.setListener(object : VoiceRecordingHelper.RecordingListener {
            override fun onRecordingStarted() {
                android.util.Log.d("MainActivity", "Recording started")
                isRecording = true
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                android.util.Log.d("MainActivity", "Recording completed: ${audioFile.absolutePath}, Duration: ${durationMs}ms")
                isRecording = false
                processAudioFile(audioFile, durationMs)
            }
            
            override fun onRecordingCancelled() {
                android.util.Log.d("MainActivity", "Recording cancelled")
                isRecording = false
            }
            
            override fun onRecordingError(error: String) {
                android.util.Log.e("MainActivity", "Recording error: $error")
                isRecording = false
                Toast.makeText(this@MainActivity, "خطا: $error", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun processAudioFile(audioFile: File, durationMs: Long) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("MainActivity", "Processing audio file: ${audioFile.absolutePath}")
                // TODO: Send to AI for analysis
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error processing audio", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceHelper.cancelRecording()
    }
}
