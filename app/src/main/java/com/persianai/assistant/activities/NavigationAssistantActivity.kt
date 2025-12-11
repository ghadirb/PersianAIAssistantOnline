package com.persianai.assistant.activities

import android.Manifest
import android.location.Location
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.persianai.assistant.databinding.ActivityAichatBinding
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.navigation.SavedLocationsManager
import com.persianai.assistant.navigation.SavedLocationsManager.SavedLocation
import com.persianai.assistant.utils.TTSHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * دستیار مکالمه‌ای مسیریابی بدون نقشه (ورودی/خروجی صوتی)
 */
class NavigationAssistantActivity : BaseChatActivity() {

    private lateinit var chatBinding: ActivityAichatBinding
    private lateinit var savedLocationsManager: SavedLocationsManager
    private lateinit var ttsHelper: TTSHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatBinding = ActivityAichatBinding.inflate(layoutInflater)
        binding = chatBinding
        setContentView(chatBinding.root)

        savedLocationsManager = SavedLocationsManager(this)
        ttsHelper = TTSHelper(this).also { it.initialize() }
        setupChatUI()
        chatBinding.manageChatsButton.setOnClickListener { showConversationManager() }
        chatBinding.chatTitle.text = "💬 دستیار مسیریابی"

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(role = MessageRole.USER, content = "سلام", timestamp = now)
        addMessage(userMessage)
        val welcome = "سلام! دستیار مسیریابی هوشمند هستم. بگو کجا می‌خوای بری تا مسیر سریع یا خلوت رو پیشنهاد بدم."
        addMessage(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = welcome,
                timestamp = now
            )
        )
        ttsHelper.speak(welcome)
    }

    override fun getRecyclerView() = chatBinding.chatRecyclerView
    override fun getMessageInput() = chatBinding.messageInput
    override fun getSendButton() = chatBinding.sendButton
    override fun getVoiceButton() = chatBinding.voiceButton
    override fun getNamespace(): String = "navigation"
    override fun getSystemPrompt(): String = """
        تو یک دستیار مسیریابی فارسی هستی که بدون نقشه داخلی کار می‌کند.
        وظایف:
        - مقصد را از کاربر بگیر و مسیر سریع/کم‌ترافیک پیشنهاد بده.
        - اگر مقصد یکی از محل‌های ذخیره‌شده (خانه، محل کار، ... ) بود، آن را تشخیص بده.
        - اگر کاربر لینک نشان/گوگل‌مپ داد، مختصات را استخراج کن.
        - پاسخ‌ها باید کوتاه و قابل خواندن صوتی باشند.
        - در قطع اینترنت، از داده ذخیره‌شده یا تخمینی استفاده کن و اعلام کن که آنلاین نیستی.
        - در صورت نداشتن لوکیشن فعلی، از کاربر بخواه GPS را روشن کند یا مبدا را بگوید.
    """.trimIndent()

    override suspend fun handleRequest(text: String): String {
        return SmartNavigationAssistant(this, savedLocationsManager).process(text)
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
    }
}

private class SmartNavigationAssistant(
    private val activity: NavigationAssistantActivity,
    private val savedLocationsManager: SavedLocationsManager
) {

    private val prefs = activity.getSharedPreferences("nav_voice_assistant", android.content.Context.MODE_PRIVATE)
    private val tts = activity.ttsHelper
    private var guidanceActive = false
    private var activeDestination: SavedLocation? = null
    private var lastSuggested: SavedLocation? = null

    suspend fun process(input: String): String = withContext(Dispatchers.Default) {
        val normalized = input.trim()

        // 1) فهرست محل‌های ذخیره شده
        if (normalized.contains("محل‌های ذخیره") || normalized.contains("جاهای ذخیره")) {
            val msg = listSavedPlaces()
            tts.speak(msg)
            return@withContext msg
        }

        // 2) شروع هدایت صوتی
        if (normalized.contains("شروع هدایت") || normalized.contains("راهنمایی") || normalized.contains("هدایت صوتی")) {
            val dest = lastSuggested ?: getRecentDestinationName()?.let { savedLocationsManager.findByName(it) }
            if (dest != null) {
                val msg = startGuidance(dest)
                tts.speak(msg)
                return@withContext msg
            }
            val msg = "اول مقصد را بگو (مثلا «برو خانه» یا لینک نقشه) بعد بگو شروع هدایت."
            tts.speak(msg)
            return@withContext msg
        }

        // 3) ریران یا گم‌شدن
        if (normalized.contains("گم شدم") || normalized.contains("مسیر جدید") || normalized.contains("دوباره مسیریابی")) {
            val msg = reroute()
            tts.speak(msg)
            return@withContext msg
        }

        // 4) توقف هدایت
        if (normalized.contains("توقف هدایت") || normalized.contains("متوقف") || normalized.contains("خاموش")) {
            guidanceActive = false
            activeDestination = null
            val msg = "هدایت صوتی متوقف شد."
            tts.speak(msg)
            return@withContext msg
        }

        // 5) درخواست مقصد
        val destination = extractDestination(normalized)
        if (destination != null) {
            lastSuggested = destination
            saveRecentDestination(destination.name)
            val suggestion = buildRouteSuggestion(destination)
            tts.speak(suggestion)
            return@withContext suggestion
        }

        // 6) پیشنهاد بر اساس عادت
        if (normalized.contains("کجا برم") || normalized.contains("مسیر بهتر") || normalized.contains("پیشنهاد")) {
            val recent = getRecentDestination()
            if (recent != null) {
                val loc = savedLocationsManager.findByName(recent)
                if (loc != null) {
                    val suggestion = buildRouteSuggestion(loc, mentionHabit = true)
                    lastSuggested = loc
                    tts.speak(suggestion)
                    return@withContext suggestion
                }
            }
            val msg = "بهترین پیشنهاد امروز: مقصد پرتکرارت رو بگو تا مسیر سریع/خلوت رو پیشنهاد بدم. می‌تونی بگی «برو خونه» یا لینک نقشه بفرستی."
            tts.speak(msg)
            return@withContext msg
        }

        val fallback = "برای شروع بگو «برو به ...» یا نام مقصد ذخیره‌شده (مثل خانه/محل کار). اگر لینک نشان/گوگل‌مپ داری، همینجا بفرست."
        tts.speak(fallback)
        return@withContext fallback
    }

    private fun listSavedPlaces(): String {
        val list = savedLocationsManager.getSavedLocations()
        return if (list.isEmpty()) {
            "هنوز جایی ذخیره نکردی. می‌تونی دستی اضافه کنی یا از نشان/گوگل‌مپ شیر کنی."
        } else {
            val names = list.joinToString("، ") { it.name }
            "مکان‌های ذخیره‌شده: $names. بگو «برو به ${list.first().name}»."
        }
    }

    private fun extractDestination(input: String): SavedLocation? {
        // 1) بر اساس نام ذخیره‌شده
        val all = savedLocationsManager.getSavedLocations()
        val byName = all.firstOrNull { input.contains(it.name) }
        if (byName != null) return byName

        // 2) لینک نقشه (مختصات) + ذخیره
        val coords = extractLatLon(input) ?: parseSharedLink(input)
        if (coords != null) {
            savedLocationsManager.upsertLocation(
                name = "مقصد اشتراکی",
                address = "",
                latLng = com.google.android.gms.maps.model.LatLng(coords.first, coords.second),
                category = "shared",
                source = "shared"
            )
            return SavedLocation(
                id = System.currentTimeMillis().toString(),
                name = "مقصد اشتراکی",
                latitude = coords.first,
                longitude = coords.second,
                address = "",
                category = "shared",
                timestamp = System.currentTimeMillis(),
                source = "shared"
            )
        }

        return null
    }

    private fun extractLatLon(text: String): Pair<Double, Double>? {
        val regex = Regex("([0-9]{1,3}\\.\\d+),\\s*([0-9]{1,3}\\.\\d+)")
        val match = regex.find(text) ?: return null
        return try {
            val lat = match.groupValues[1].toDouble()
            val lon = match.groupValues[2].toDouble()
            lat to lon
        } catch (_: Exception) {
            null
        }
    }

    /**
     * تشخیص لینک‌های share نشان/گوگل‌مپ و استخراج مختصات
     */
    private fun parseSharedLink(text: String): Pair<Double, Double>? {
        // نشن: https://neshan.org/maps/35.123,51.456/...
        val neshan = Regex("neshan\\.org/maps/([0-9]{1,3}\\.\\d+),([0-9]{1,3}\\.\\d+)", RegexOption.IGNORE_CASE)
        neshan.find(text)?.let {
            return try {
                it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
            } catch (_: Exception) { null }
        }
        // گوگل‌مپ: .../@35.123,51.456 or q=35.123,51.456
        val g1 = Regex("@([0-9]{1,3}\\.\\d+),([0-9]{1,3}\\.\\d+)", RegexOption.IGNORE_CASE)
        g1.find(text)?.let {
            return try {
                it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
            } catch (_: Exception) { null }
        }
        val g2 = Regex("q=([0-9]{1,3}\\.\\d+),([0-9]{1,3}\\.\\d+)", RegexOption.IGNORE_CASE)
        g2.find(text)?.let {
            return try {
                it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
            } catch (_: Exception) { null }
        }
        return null
    }

    private fun buildRouteSuggestion(dest: SavedLocation, mentionHabit: Boolean = false): String {
        val origin = getLastKnownLocation()
        val distanceKm = origin?.let { haversineKm(it.latitude, it.longitude, dest.latitude, dest.longitude) }
        val eta = distanceKm?.let { estimateEtaMinutes(it) }
        val habitNote = if (mentionHabit) " (از عادت‌های قبلی)" else ""

        val sb = StringBuilder()
        sb.append("مقصد: ${dest.name}$habitNote\n")
        if (distanceKm != null) {
            sb.append("مسافت تقریبی: ${"%.1f".format(distanceKm)} کیلومتر\n")
        } else {
            sb.append("مسافت: نیاز به موقعیت فعلی یا GPS روشن.\n")
        }
        if (eta != null) {
            sb.append("زمان تخمینی: ${eta.roundToInt()} دقیقه (مسیر سریع)\n")
            sb.append("اگر ترافیک سبک‌تر می‌خوای، ۱۰ دقیقه بعد حرکت کنی ترافیک کمتر می‌شود.\n")
        } else {
            sb.append("برای زمان تقریبی، GPS را روشن کن یا مبدا را بگو.\n")
        }
        sb.append("بگو «شروع هدایت» تا راهنمای صوتی فعال شود.")
        return sb.toString()
    }

    private fun startGuidance(dest: SavedLocation): String {
        val origin = getLastKnownLocation()
        if (origin == null) {
            return "هدایت فعال نشد؛ GPS را روشن کن یا اجازه دسترسی بده."
        }
        guidanceActive = true
        activeDestination = dest
        lastSuggested = dest
        val distanceKm = haversineKm(origin.latitude, origin.longitude, dest.latitude, dest.longitude)
        val eta = estimateEtaMinutes(distanceKm).roundToInt()
        val msg = "هدایت به ${dest.name} شروع شد. مسافت تقریبی ${"%.1f".format(distanceKm)} کیلومتر و زمان حدود $eta دقیقه. هنگام انحراف بگو «مسیر جدید»."
        return msg
    }

    private fun reroute(): String {
        val dest = activeDestination ?: lastSuggested
        if (dest == null) return "مقصدی برای ریران نیست. ابتدا بگو کجا می‌خوای بری."
        val origin = getLastKnownLocation()
        if (origin == null) return "برای ریران نیاز به GPS روشن است."
        val distanceKm = haversineKm(origin.latitude, origin.longitude, dest.latitude, dest.longitude)
        val eta = estimateEtaMinutes(distanceKm).roundToInt()
        val msg = "مسیر جدید به ${dest.name}: حدود ${"%.1f".format(distanceKm)} کیلومتر و $eta دقیقه. مستقیم ادامه بده و در تقاطع بعدی مسیر کم‌ترافیک را پیشنهاد می‌کنم."
        return msg
    }

    private fun getLastKnownLocation(): Location? {
        // تلاش ساده با LocationManager از PermissionsHelper
        return try {
            val lm = ContextCompat.getSystemService(activity, android.location.LocationManager::class.java)
            if (lm != null) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        2001
                    )
                    null
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private fun estimateEtaMinutes(distanceKm: Double): Double {
        val speedKmh = 40.0 // میانگین شهری
        return (distanceKm / speedKmh) * 60.0
    }

    private fun saveRecentDestination(name: String) {
        prefs.edit().putString("recent_dest", name).apply()
    }

    private fun getRecentDestination(): String? = prefs.getString("recent_dest", null)
    private fun getRecentDestinationName(): String? = prefs.getString("recent_dest", null)
}
