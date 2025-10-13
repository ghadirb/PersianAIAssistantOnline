package com.persianai.assistant.activities

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.persianai.assistant.R
import com.persianai.assistant.api.AIModelManager
import kotlinx.coroutines.*

class ApiSettingsActivity : AppCompatActivity() {
    
    private lateinit var modelManager: AIModelManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // UI Components
    private lateinit var openAICard: CardView
    private lateinit var openAIKeyInput: TextInputEditText
    private lateinit var openAIStatus: TextView
    private lateinit var openAITestButton: Button
    private lateinit var openAIToggle: ImageButton
    
    private lateinit var claudeCard: CardView
    private lateinit var claudeKeyInput: TextInputEditText
    private lateinit var claudeStatus: TextView
    private lateinit var claudeTestButton: Button
    private lateinit var claudeToggle: ImageButton
    
    private lateinit var openRouterCard: CardView
    private lateinit var openRouterKeyInput: TextInputEditText
    private lateinit var openRouterStatus: TextView
    private lateinit var openRouterTestButton: Button
    private lateinit var openRouterToggle: ImageButton
    
    private lateinit var aimlCard: CardView
    private lateinit var aimlKeyInput: TextInputEditText
    private lateinit var aimlStatus: TextView
    private lateinit var aimlTestButton: Button
    private lateinit var aimlToggle: ImageButton
    
    private lateinit var availableModelsText: TextView
    private lateinit var progressBar: ProgressBar
    
    private val prefs by lazy { getSharedPreferences("api_keys", MODE_PRIVATE) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_settings)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "تنظیمات API"
        }
        
        modelManager = AIModelManager(this)
        initializeViews()
        loadSavedKeys()
        updateAvailableModels()
    }
    
    private fun initializeViews() {
        // OpenAI
        openAICard = findViewById(R.id.openAICard)
        openAIKeyInput = findViewById(R.id.openAIKeyInput)
        openAIStatus = findViewById(R.id.openAIStatus)
        openAITestButton = findViewById(R.id.openAITestButton)
        openAIToggle = findViewById(R.id.openAIToggle)
        
        setupApiCard(
            keyInput = openAIKeyInput,
            testButton = openAITestButton,
            toggleButton = openAIToggle,
            statusText = openAIStatus,
            provider = "openai",
            providerName = "OpenAI"
        )
        
        // Claude (Anthropic)
        claudeCard = findViewById(R.id.claudeCard)
        claudeKeyInput = findViewById(R.id.claudeKeyInput)
        claudeStatus = findViewById(R.id.claudeStatus)
        claudeTestButton = findViewById(R.id.claudeTestButton)
        claudeToggle = findViewById(R.id.claudeToggle)
        
        setupApiCard(
            keyInput = claudeKeyInput,
            testButton = claudeTestButton,
            toggleButton = claudeToggle,
            statusText = claudeStatus,
            provider = "claude",
            providerName = "Claude (Anthropic)"
        )
        
        // OpenRouter
        openRouterCard = findViewById(R.id.openRouterCard)
        openRouterKeyInput = findViewById(R.id.openRouterKeyInput)
        openRouterStatus = findViewById(R.id.openRouterStatus)
        openRouterTestButton = findViewById(R.id.openRouterTestButton)
        openRouterToggle = findViewById(R.id.openRouterToggle)
        
        setupApiCard(
            keyInput = openRouterKeyInput,
            testButton = openRouterTestButton,
            toggleButton = openRouterToggle,
            statusText = openRouterStatus,
            provider = "openrouter",
            providerName = "OpenRouter"
        )
        
        // AIML API
        aimlCard = findViewById(R.id.aimlCard)
        aimlKeyInput = findViewById(R.id.aimlKeyInput)
        aimlStatus = findViewById(R.id.aimlStatus)
        aimlTestButton = findViewById(R.id.aimlTestButton)
        aimlToggle = findViewById(R.id.aimlToggle)
        
        setupApiCard(
            keyInput = aimlKeyInput,
            testButton = aimlTestButton,
            toggleButton = aimlToggle,
            statusText = aimlStatus,
            provider = "aiml",
            providerName = "AIML API"
        )
        
        availableModelsText = findViewById(R.id.availableModelsText)
        progressBar = findViewById(R.id.progressBar)
        
        // Instructions button
        findViewById<Button>(R.id.instructionsButton)?.setOnClickListener {
            showInstructions()
        }
    }
    
    private fun setupApiCard(
        keyInput: TextInputEditText,
        testButton: Button,
        toggleButton: ImageButton,
        statusText: TextView,
        provider: String,
        providerName: String
    ) {
        var isPasswordVisible = false
        
        // Toggle password visibility
        toggleButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            keyInput.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            toggleButton.setImageResource(
                if (isPasswordVisible) R.drawable.ic_visibility_off
                else R.drawable.ic_visibility
            )
            keyInput.setSelection(keyInput.text?.length ?: 0)
        }
        
        // Save key on text change
        keyInput.addTextChangedListener { text ->
            val key = text.toString().trim()
            if (key.isNotEmpty()) {
                modelManager.saveApiKey(provider, key)
                updateAvailableModels()
            }
        }
        
        // Test API key
        testButton.setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isEmpty()) {
                statusText.text = "❌ لطفاً کلید API را وارد کنید"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                return@setOnClickListener
            }
            
            progressBar.visibility = View.VISIBLE
            testButton.isEnabled = false
            statusText.text = "🔄 در حال بررسی..."
            
            scope.launch {
                modelManager.validateApiKey(provider, key) { isValid, message ->
                    progressBar.visibility = View.GONE
                    testButton.isEnabled = true
                    statusText.text = message
                    statusText.setTextColor(
                        if (isValid) getColor(android.R.color.holo_green_dark)
                        else getColor(android.R.color.holo_red_dark)
                    )
                    
                    if (isValid) {
                        modelManager.saveApiKey(provider, key)
                        updateAvailableModels()
                    }
                }
            }
        }
    }
    
    private fun loadSavedKeys() {
        openAIKeyInput.setText(prefs.getString("openai_api_key", ""))
        claudeKeyInput.setText(prefs.getString("claude_api_key", ""))
        openRouterKeyInput.setText(prefs.getString("openrouter_api_key", ""))
        aimlKeyInput.setText(prefs.getString("aiml_api_key", ""))
    }
    
    private fun updateAvailableModels() {
        val models = modelManager.getAvailableModels()
        
        if (models.isEmpty()) {
            availableModelsText.text = """
                ⚠️ هیچ مدلی در دسترس نیست
                
                لطفاً حداقل یک کلید API معتبر وارد کنید
            """.trimIndent()
            availableModelsText.setTextColor(getColor(android.R.color.holo_orange_dark))
        } else {
            val modelsText = buildString {
                append("✅ مدل‌های در دسترس:\n\n")
                models.forEachIndexed { index, model ->
                    append("${index + 1}. ${model.displayName} (${model.provider})\n")
                    model.features.forEach { feature ->
                        append("   • $feature\n")
                    }
                    append("\n")
                }
            }
            availableModelsText.text = modelsText
            availableModelsText.setTextColor(getColor(android.R.color.darker_gray))
        }
    }
    
    private fun showInstructions() {
        AlertDialog.Builder(this)
            .setTitle("راهنمای دریافت API Key")
            .setMessage("""
                📝 دستورالعمل دریافت کلید API:
                
                🔸 OpenAI:
                1. به platform.openai.com بروید
                2. ثبت نام/ورود کنید
                3. به API Keys → Create new secret key بروید
                4. کلید را کپی کنید (با sk- شروع می‌شود)
                
                🔸 Claude (Anthropic):
                1. به console.anthropic.com بروید
                2. ثبت نام/ورود کنید
                3. به API Keys بروید
                4. کلید جدید بسازید و کپی کنید
                
                🔸 OpenRouter:
                1. به openrouter.ai بروید
                2. ثبت نام/ورود کنید
                3. به Keys → Create Key بروید
                4. 5$ اعتبار رایگان دریافت کنید
                
                🔸 AIML API:
                1. به aimlapi.com بروید
                2. ثبت نام رایگان کنید
                3. در Dashboard کلید API را کپی کنید
                
                💡 نکات مهم:
                • کلیدها را محرمانه نگه دارید
                • از کلیدهای رایگان برای تست استفاده کنید
                • برای Whisper (صدا به متن) کلید OpenAI نیاز است
            """.trimIndent())
            .setPositiveButton("متوجه شدم", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
