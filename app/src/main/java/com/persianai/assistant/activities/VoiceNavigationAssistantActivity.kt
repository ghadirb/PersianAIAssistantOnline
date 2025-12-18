package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.services.UnifiedVoiceEngine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.persianai.assistant.databinding.ActivityVoiceNavigationAssistantBinding
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.navigation.NessanMapsAPI
import com.persianai.assistant.navigation.SavedLocationsManager
import com.persianai.assistant.utils.LocationShareParser
import com.persianai.assistant.utils.NeshanDirectionAPI
import com.persianai.assistant.utils.NeshanSearchAPI
import com.persianai.assistant.utils.SharedDataManager
import com.persianai.assistant.utils.TTSHelper
import com.persianai.assistant.ai.AIClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class VoiceNavigationAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceNavigationAssistantBinding
    private lateinit var ttsHelper: TTSHelper
    private lateinit var savedLocationsManager: SavedLocationsManager
    private lateinit var neshanDirectionAPI: NeshanDirectionAPI
    private lateinit var neshanSearchAPI: NeshanSearchAPI
    private lateinit var nessanMapsAPI: NessanMapsAPI
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefsManager: PreferencesManager
    private var aiClient: AIClient? = null
    private var currentModel: AIModel = AIModel.QWEN_2_5_1B5
    private val httpClient = OkHttpClient()

    private var lastDestination: LatLng? = null
    private var lastDestinationName: String = ""
    private var lastDestinationAddress: String = ""
    private var pendingDest: LatLng? = null
    private var pendingDestName: String = ""
    private var pendingDestAddress: String = ""
    private var voiceEngine: UnifiedVoiceEngine? = null
    private var isRecording = false
    private val hfApiKey: String by lazy {
        getSharedPreferences("api_keys", MODE_PRIVATE)
            .getString("hf_api_key", null)
            ?: DefaultApiKeys.getHuggingFaceKey().orEmpty()
    }

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spokenText =
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    binding.transcriptText.text = spokenText
                    binding.statusText.text = "در حال پردازش فرمان..."
                    respondToCommand(spokenText)
                } else {
                    binding.statusText.text = "متنی دریافت نشد"
                }
            } else {
                binding.statusText.text = "تشخیص گفتار لغو شد"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceNavigationAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsHelper = TTSHelper(this)
        ttsHelper.initialize()
        savedLocationsManager = SavedLocationsManager(this)
        voiceEngine = UnifiedVoiceEngine(this)
        neshanDirectionAPI = NeshanDirectionAPI()
        neshanSearchAPI = NeshanSearchAPI(this)
        nessanMapsAPI = NessanMapsAPI()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefsManager = PreferencesManager(this)
        setupAiClient()

        setupToolbar()
        setupMicButton()
        setupVoiceFab()
        handleIncomingIntent(intent)
    }

    private fun setupVoiceFab() {
        try {
            binding.voiceFab.setOnClickListener {
                // Reuse existing speech recognition / recording behavior
                checkAudioPermissionAndStart()
            }
        } catch (e: Exception) {
            // binding may not include the FAB in some layouts
            android.util.Log.w("VoiceNav", "voiceFab not available", e)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupMicButton() {
        binding.micButton.setOnClickListener {
            checkAudioPermissionAndStart() // حالت تبدیل صوت به متن (SpeechRecognizer)
        }

        binding.micButton.setOnLongClickListener {
            // حالت ضبط فایل صوتی کامل و ارسال به مدل HuggingFace
            startVoiceRecording()
            true
        }

        binding.micButton.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && isRecording) {
                stopRecordingAndTranscribe()
                true
            } else {
                false
            }
        }

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text?.toString().orEmpty().trim()
            sendTextToModel(text)
        }
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            val text = binding.messageInput.text?.toString().orEmpty().trim()
            if (text.isNotEmpty()) {
                sendTextToModel(text)
            }
            true
        }
    }

    private fun setupAiClient() {
        val keys = prefsManager.getAPIKeys()
        if (keys.isNotEmpty()) {
            aiClient = AIClient(keys)
            currentModel = prefsManager.getSelectedModel()
        }
    }

    private fun checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startSpeechRecognition()
        }
    }

    private fun startSpeechRecognition() {
        binding.statusText.text = "در حال گوش‌دادن..."
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "لطفاً مقصد یا فرمان مسیریابی را بگویید")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            binding.statusText.text = "خطا در شروع تشخیص گفتار"
            Toast.makeText(this, "سرویس گفتار در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }

    private fun respondToCommand(text: String) {
        val lower = text.lowercase()
        when {
            lower.contains("خانه") -> routeToSaved("home", "خانه")
            lower.contains("کار") -> routeToSaved("work", "محل کار")
            lower.contains("ذخیره") -> {
                handleSaveCommand(text)
            }
            lower.contains("لیست") || lower.contains("مکان های ذخیره") || lower.contains("مکان‌های ذخیره") -> {
                listSavedLocations()
            }
            lower.contains("برو ") || lower.contains("به ") -> {
                navigateToNamedLocation(text)
            }
            lower.contains("پیام") || lower.contains("چت") -> {
                binding.statusText.text = "پیام در حال ارسال به مدل..."
                sendTextToModel(text.replace("پیام", "", true).replace("چت", "", true).trim())
            }
            else -> searchAndRoute(text)
        }
    }

    private fun routeToSaved(category: String, label: String) {
        val dest = when (category) {
            "home" -> savedLocationsManager.getHome()
            "work" -> savedLocationsManager.getWork()
            else -> null
        }
        if (dest == null) {
            val msg = "$label ثبت نشده است. بعداً با فرمان ذخیره یا اشتراک نقشه اضافه می‌کنیم."
            binding.statusText.text = msg
            ttsHelper.speak(msg)
            return
        }
        val latLng = LatLng(dest.latitude, dest.longitude)
        fetchRoute(latLng, dest.name, dest.address)
    }

    private fun searchAndRoute(query: String) {
        lifecycleScope.launch {
            binding.statusText.text = "در حال جستجو برای: $query"
            val destination = withContext(Dispatchers.IO) {
                neshanSearchAPI.searchGlobal(query).firstOrNull()?.let {
                    LatLng(it.latitude, it.longitude) to (it.title.ifBlank { query })
                }
            }

            if (destination == null) {
                val msg = "نتیجه‌ای پیدا نشد. لطفاً مقصد را دقیق‌تر بگویید یا لوکیشن را به اشتراک بگذارید."
                binding.statusText.text = msg
                ttsHelper.speak(msg)
                return@launch
            }
            fetchRoute(destination.first, destination.second, "")
        }
    }

    data class RouteSummary(
        val distanceKm: Double,
        val durationMin: Int,
        val steps: List<String>
    )

    private fun fetchRoute(dest: LatLng, destName: String, destAddress: String) {
        lifecycleScope.launch {
            binding.statusText.text = "در حال دریافت مسیر..."
            pendingDest = null
            pendingDestName = ""
            pendingDestAddress = ""

            if (!hasLocationPermission()) {
                pendingDest = dest
                pendingDestName = destName
                pendingDestAddress = destAddress
                requestLocationPermission()
                binding.statusText.text = "برای محاسبه مسیر به مجوز مکان نیاز داریم."
                ttsHelper.speak("لطفاً دسترسی مکان را فعال کنید")
                return@launch
            }

            val origin = getCurrentLocationOrFallback()

            val route = withContext(Dispatchers.IO) {
                neshanDirectionAPI.getDirection(origin.latitude, origin.longitude, dest.latitude, dest.longitude)
                    .firstOrNull()
                    ?.let { RouteSummary(it.distance, it.duration, it.steps) }
                    ?: nessanMapsAPI.getDirections(origin, dest)
                        ?.let { RouteSummary(it.distance, it.duration, it.instructions) }
            }

            if (route == null) {
                val msg = "مسیر پیدا نشد. بعداً دوباره تلاش کنید یا مقصد را تغییر دهید."
                binding.statusText.text = msg
                ttsHelper.speak(msg)
                return@launch
            }

            val summary = "مسافت تقریبی ${"%.1f".format(route.distanceKm)} کیلومتر، زمان ${route.durationMin} دقیقه."
            val instructionsPreview = route.steps.take(2).joinToString(" / ").ifBlank { "دستورالعمل‌ها آماده‌اند." }

            binding.statusText.text = "مسیر پیشنهادی به $destName"
            binding.routeSummaryText.text = "$summary\n$instructionsPreview"

            ttsHelper.speak("مسیر به $destName آماده شد. $summary")

            lastDestination = dest
            lastDestinationName = destName
            lastDestinationAddress = destAddress
        }
    }

    // Stub for future reroute when deviation detected
    private fun handleReroute() {
        binding.statusText.text = "خارج از مسیر. در حال محاسبه مسیر جدید..."
        ttsHelper.speak("از مسیر خارج شدید. مسیر جدید محاسبه می‌شود.")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startSpeechRecognition()
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            binding.statusText.text = "مجوز میکروفون لازم است"
            Toast.makeText(this, "بدون مجوز میکروفون امکان شنیدن نیست", Toast.LENGTH_SHORT).show()
        } else if (requestCode == REQUEST_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingDest != null) {
                fetchRoute(pendingDest!!, pendingDestName, pendingDestAddress)
            }
        }
    }

    private fun startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }

        lifecycleScope.launch {
            binding.statusText.text = "در حال ضبط صوت..."
            val res = voiceEngine?.startRecording()
            if (res?.isSuccess == true) {
                isRecording = true
                binding.statusText.text = "در حال ضبط صوت... برای توقف رها کنید"
            } else {
                isRecording = false
                binding.statusText.text = "خطا در شروع ضبط"
                Toast.makeText(this@VoiceNavigationAssistantActivity, "خطا در ضبط صدا", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        lifecycleScope.launch {
            binding.statusText.text = "در حال توقف ضبط و تحلیل..."
            val stopResult = voiceEngine?.stopRecording()
            isRecording = false

            val recordingResult = stopResult?.getOrNull()
            val file = recordingResult?.file
            if (file == null || !file.exists()) {
                binding.statusText.text = "فایل ضبط‌شده در دسترس نیست"
                return@launch
            }

            binding.statusText.text = "در حال تحلیل هیبریدی..."
            val hybrid = voiceEngine?.analyzeHybrid(file)
            val primary = hybrid?.getOrNull()?.primaryText

            if (!primary.isNullOrBlank()) {
                binding.transcriptText.text = primary
                binding.statusText.text = "متن استخراج شد. در حال پردازش فرمان..."
                respondToCommand(primary)
                return@launch
            }

            // Fallback to previous HF transcription if hybrid failed
            binding.statusText.text = "تحلیل محلی ناموفق، ارسال به سرویس آنلاین..."
            val text = transcribeWithHuggingFace(file)
            if (text.isNullOrBlank()) {
                binding.statusText.text = if (hfApiKey.isBlank()) {
                    "کلید HuggingFace تنظیم نیست. لطفاً کلید را وارد کنید."
                } else {
                    "تحلیل صوت ناموفق بود. دوباره تلاش کنید."
                }
                return@launch
            }
            binding.transcriptText.text = text
            binding.statusText.text = "متن استخراج شد. در حال پردازش فرمان..."
            respondToCommand(text)
        }
    }

    private suspend fun transcribeWithHuggingFace(file: File): String? = withContext(Dispatchers.IO) {
        if (hfApiKey.isBlank()) return@withContext null
        return@withContext try {
            val bytes = file.readBytes()
            val body = bytes.toRequestBody("audio/m4a".toMediaType())
            val request = Request.Builder()
                .url("https://api-inference.huggingface.co/models/openai/whisper-large-v3")
                .addHeader("Authorization", "Bearer $hfApiKey")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.e("HF-STT", "Failed: ${resp.code} ${resp.message}")
                    return@use null
                }
                val responseText = resp.body?.string()?.trim() ?: return@use null
                // HF گاهی خروجی متنی ساده می‌دهد، گاهی JSON. هر دو را پوشش می‌دهیم.
                if (responseText.startsWith("{")) {
                    try {
                        val json = org.json.JSONObject(responseText)
                        json.optString("text").ifBlank { json.optString("generated_text") }
                    } catch (_: Exception) {
                        responseText
                    }
                } else {
                    responseText
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HF-STT", "error: ${e.message}", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 2010
        private const val REQUEST_LOCATION = 2011
        private val TEHRAN = LatLng(35.6892, 51.3890)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val dataUri = intent.data
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val parsed = LocationShareParser.parseFromUri(dataUri)
            ?: LocationShareParser.parseFromIntentText(sharedText)

        if (parsed != null) {
            val latLng = LatLng(parsed.latitude, parsed.longitude)
            val suggestedName = parsed.label?.takeIf { it.isNotBlank() } ?: "مکان ذخیره‌شده"
            promptSaveSharedLocation(latLng, suggestedName)
            return
        }

        if (action == Intent.ACTION_VIEW && dataUri != null) {
            val location = dataUri.getQueryParameter("location") ?: ""
            binding.messageInput.setText(location)
            sendTextToModel(location)
        }
    }

    private fun promptSaveSharedLocation(latLng: LatLng, defaultName: String) {
        val input = EditText(this).apply { setText(defaultName) }
        AlertDialog.Builder(this)
            .setTitle("ذخیره مکان دریافت‌شده")
            .setMessage("نام دلخواه را وارد کنید تا در مسیریاب صوتی ذخیره شود.")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = input.text.toString().ifBlank { defaultName }
                val saved = savedLocationsManager.saveLocation(name, "", latLng, "favorite")
                if (saved) {
                    binding.statusText.text = "✅ مکان ذخیره شد: $name"
                    ttsHelper.speak("مکان $name ذخیره شد")
                } else {
                    binding.statusText.text = "❌ ذخیره مکان ناموفق بود"
                    ttsHelper.speak("ذخیره مکان انجام نشد")
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun handleSaveCommand(text: String) {
        val dest = lastDestination
        if (dest == null) {
            val reply = "مقصدی برای ذخیره ندارم. لطفاً ابتدا مقصد را بگویید یا از اشتراک‌گذاری نقشه استفاده کنید."
            binding.statusText.text = reply
            ttsHelper.speak(reply)
            return
        }

        val lower = text.lowercase()
        val name = text.replace("ذخیره", "", true)
            .replace("کن", "", true)
            .trim()
            .ifBlank { lastDestinationName.ifBlank { "مکان ذخیره شده" } }

        val category = when {
            lower.contains("خانه") -> "home"
            lower.contains("کار") -> "work"
            else -> "favorite"
        }

        val saved = when (category) {
            "home" -> {
                savedLocationsManager.setHome(dest, lastDestinationAddress)
                true
            }
            "work" -> {
                savedLocationsManager.setWork(dest, lastDestinationAddress)
                true
            }
            else -> savedLocationsManager.saveLocation(name, lastDestinationAddress, dest, "favorite")
        }

        val reply = if (saved) "ذخیره شد: $name" else "خطا در ذخیره مکان"
        binding.statusText.text = reply
        ttsHelper.speak(reply)
    }

    private fun listSavedLocations() {
        val list = savedLocationsManager.getAllLocations()
        if (list.isEmpty()) {
            val msg = "هیچ مکانی ذخیره نشده است."
            binding.statusText.text = msg
            ttsHelper.speak(msg)
            return
        }
        val items = list.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("مکان‌های ذخیره شده")
            .setItems(items) { _, which ->
                val loc = list[which]
                val options = arrayOf("مسیریابی", "تغییر نام", "حذف")
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(loc.name)
                    .setItems(options) { _, opt ->
                        when (opt) {
                            0 -> fetchRoute(LatLng(loc.latitude, loc.longitude), loc.name, loc.address)
                            1 -> promptRenameLocation(loc.id, loc.name)
                            2 -> {
                                savedLocationsManager.deleteLocation(loc.id)
                                Toast.makeText(this, "حذف شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun navigateToNamedLocation(text: String) {
        val query = text.replace("برو", "", ignoreCase = true)
            .replace("به", "", ignoreCase = true)
            .trim()
        if (query.isBlank()) {
            searchAndRoute(text)
            return
        }
        val all = savedLocationsManager.getAllLocations()
        val target = all.firstOrNull { it.name.contains(query, true) || it.address.contains(query, true) }
        if (target != null) {
            fetchRoute(LatLng(target.latitude, target.longitude), target.name, target.address)
            return
        }
        // اگر ذخیره نبود، جستجوی آنلاین
        searchAndRoute(query)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION
        )
    }

    private suspend fun getCurrentLocationOrFallback(): LatLng = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext TEHRAN

        val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { cont.resume(it) {} }
                .addOnFailureListener { cont.resume(null) {} }
        }

        if (loc != null) {
            SharedDataManager.saveLastLocation(this@VoiceNavigationAssistantActivity, loc.latitude, loc.longitude)
            return@withContext LatLng(loc.latitude, loc.longitude)
        }

        SharedDataManager.getLastLocation(this@VoiceNavigationAssistantActivity)?.let {
            return@withContext LatLng(it.first, it.second)
        }

        return@withContext TEHRAN
    }

    private fun promptSaveSharedLocation(parsed: LocationShareParser.ParsedLocation) {
        val input = EditText(this).apply {
            hint = "نام مکان"
            setText(parsed.label?.takeIf { it.isNotBlank() } ?: "")
        }
        val latLng = LatLng(parsed.latitude, parsed.longitude)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("ذخیره مکان اشتراک‌گذاری‌شده")
            .setMessage("مکان دریافت شد. یک نام انتخاب کنید.")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val label = input.text.toString().ifBlank { "مکان ذخیره شده" }
                val saved = savedLocationsManager.saveLocation(label, "", latLng, "favorite")
                lastDestination = latLng
                lastDestinationName = label
                lastDestinationAddress = ""
                val msg = if (saved) "مکان «$label» ذخیره شد و آماده مسیریابی است." else "ذخیره‌سازی انجام نشد."
                binding.statusText.text = msg
                binding.routeSummaryText.text = "مختصات: ${parsed.latitude}, ${parsed.longitude}"
                ttsHelper.speak(msg)
            }
            .setNegativeButton("صرف‌نظر", null)
            .show()
    }

    private fun promptRenameLocation(id: String, currentName: String) {
        val input = EditText(this).apply {
            hint = "نام جدید"
            setText(currentName)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("تغییر نام مکان")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val newName = input.text.toString().ifBlank { currentName }
                if (savedLocationsManager.updateLocationName(id, newName)) {
                    Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "خطا در تغییر نام", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun sendTextToModel(text: String) {
        val client = aiClient ?: run {
            Toast.makeText(this, "کلید API تنظیم نشده است", Toast.LENGTH_SHORT).show()
            return
        }
        val message = text.ifBlank { return }
        lifecycleScope.launch {
            try {
                val resp = client.sendMessage(currentModel, listOf(com.persianai.assistant.models.ChatMessage(role = com.persianai.assistant.models.MessageRole.USER, content = message)))
                binding.statusText.text = "پاسخ مدل: ${resp.content}"
                binding.transcriptText.text = resp.content
                ttsHelper.speak(resp.content)
            } catch (e: Exception) {
                binding.statusText.text = "خطا در ارسال پیام: ${e.message}"
            }
        }
    }
}
