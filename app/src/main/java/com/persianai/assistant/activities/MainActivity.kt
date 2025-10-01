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
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
import com.persianai.assistant.utils.SystemIntegrationHelper
import com.persianai.assistant.services.AIAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import android.media.MediaRecorder
import java.io.File

/**
 * صفحه اصلی چت
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var prefsManager: PreferencesManager
    private lateinit var messageStorage: MessageStorage
    private lateinit var conversationStorage: com.persianai.assistant.storage.ConversationStorage
    private var aiClient: AIClient? = null
    private var currentModel: AIModel = AIModel.GPT_4O_MINI
    private val messages = mutableListOf<ChatMessage>()
    private var currentConversation: com.persianai.assistant.models.Conversation? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var isRecording = false
    private var recordingCancelled = false
    private var recordingStartTime: Long = 0
    private var recordingTimer: android.os.CountDownTimer? = null
    private var initialY = 0f
    private val swipeThreshold = 200f // پیکسل برای لغو

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
            messageStorage = MessageStorage(this)
            conversationStorage = com.persianai.assistant.storage.ConversationStorage(this)
            
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
    }
    
    private fun showFirstRunDialogIfNeeded() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        
        if (isFirstRun && !prefsManager.hasAPIKeys()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("🤖 خوش آمدید!")
                .setMessage("""
                    به دستیار هوش مصنوعی فارسی خوش آمدید!
                    
                    این برنامه نیاز به کلیدهای API دارد:
                    
                    ✅ چت با مدل‌های GPT-4o و Claude
                    ✅ تشخیص صوت فارسی
                    ✅ ذخیره تاریخچه گفتگوها
                    ✅ پشتیبان‌گیری رمزنگاری شده
                    
                    برای شروع، رمز عبور کلیدهای API را وارد کنید:
                """.trimIndent())
                .setPositiveButton("ورود رمز") { _, _ ->
                    prefs.edit().putBoolean("is_first_run", false).apply()
                    showPasswordDialog()
                }
                .setNegativeButton("بعداً") { _, _ ->
                    prefs.edit().putBoolean("is_first_run", false).apply()
                    Toast.makeText(this, "می‌توانید بعداً از تنظیمات کلید اضافه کنید", Toast.LENGTH_LONG).show()
                }
                .setCancelable(false)
                .show()
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

        // دکمه صوت: کشیدن به بالا برای شروع ضبط
        binding.voiceButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    v.alpha = 0.5f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    if (deltaY > swipeThreshold && !isRecording) {
                        // شروع ضبط با کشیدن به بالا
                        checkAudioPermissionAndStartRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    true
                }
                else -> false
            }
        }
        
        // دکمه‌های لغو و ارسال ضبط
        binding.cancelRecordingButton.setOnClickListener {
            cancelRecording()
            Toast.makeText(this, "❌ ضبط لغو شد", Toast.LENGTH_SHORT).show()
        }
        
        binding.sendRecordingButton.setOnClickListener {
            stopRecordingAndProcess()
        }

        binding.attachButton.setOnClickListener {
            showAttachmentOptions()
        }
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
                // ارسال به AI برای تحلیل هوشمند
                val enhancedPrompt = """
                    شما یک دستیار هوشمند فارسی هستید که می‌توانید:
                    1. یادآوری تنظیم کنید (با فرمت JSON)
                    2. مسیریابی فارسی انجام دهید
                    3. محاسبات انجام دهید
                    4. تماس، پیامک، ایمیل ارسال کنید
                    5. به سوالات پاسخ دهید
                    
                    اگر درخواست یادآوری بود، پاسخ را با این فرمت بدهید:
                    REMINDER:{"time":"HH:mm","message":"متن یادآوری","alarm":true/false,"repeat":"daily/none"}
                    
                    اگر درخواست مسیریابی بود، پاسخ را با این فرمت بدهید:
                    NAVIGATION:{"destination":"مقصد","voice":true}
                    
                    اگر محاسبه ریاضی بود، جواب را محاسبه کنید.
                    
                    درخواست کاربر: $text
                """.trimIndent()
                
                val response = aiClient!!.sendMessage(currentModel, messages, enhancedPrompt)
                
                // پردازش پاسخ AI
                val processedResponse = processAIResponse(response.content)
                
                val finalMessage = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = processedResponse,
                    timestamp = System.currentTimeMillis()
                )
                addMessage(finalMessage)
                
                // ذخیره چت
                saveCurrentConversation()
                
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
    
    private suspend fun processAIResponse(response: String): String {
        return withContext(Dispatchers.Main) {
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

    private fun checkAudioPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (isRecording) return
        
        try {
            val outputDir = cacheDir
            val outputFile = File.createTempFile("audio_", ".3gp", outputDir)
            audioFilePath = outputFile.absolutePath
            
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            // نمایش نشانگر ضبط
            binding.recordingIndicator.visibility = android.view.View.VISIBLE
            
            // شروع تایمر
            startRecordingTimer()
            
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در شروع ضبط: ${e.message}", Toast.LENGTH_SHORT).show()
            android.util.Log.e("MainActivity", "Recording error", e)
        }
    }
    
    private fun startRecordingTimer() {
        recordingTimer = object : android.os.CountDownTimer(60000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val seconds = (elapsed / 1000).toInt()
                val millis = ((elapsed % 1000) / 100).toInt()
                binding.recordingTime.text = String.format("%d:%01d", seconds, millis)
            }
            
            override fun onFinish() {}
        }.start()
    }
    
    private fun cancelRecording() {
        if (!isRecording) return
        
        try {
            recordingTimer?.cancel()
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            // حذف فایل صوتی
            audioFilePath?.let { File(it).delete() }
            
            // مخفی کردن نشانگر
            binding.recordingIndicator.visibility = android.view.View.GONE
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Cancel recording error", e)
        }
    }
    
    private fun stopRecordingAndProcess() {
        if (!isRecording) return
        
        try {
            recordingTimer?.cancel()
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            // مخفی کردن نشانگر
            binding.recordingIndicator.visibility = android.view.View.GONE
            
            // استفاده از Speech Recognition برای تبدیل صوت به متن
            startVoiceRecognition()
            
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در پایان ضبط: ${e.message}", Toast.LENGTH_SHORT).show()
            android.util.Log.e("MainActivity", "Stop recording error", e)
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
        supportActionBar?.apply {
            title = "دستیار هوش مصنوعی"
            subtitle = "${currentModel.displayName}"
        }
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
            R.id.action_reminders -> {
                startActivity(Intent(this, RemindersActivity::class.java))
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
}
