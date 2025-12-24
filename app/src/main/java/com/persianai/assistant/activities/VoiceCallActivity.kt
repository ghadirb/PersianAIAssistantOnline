package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.R
import com.persianai.assistant.services.RecordingResult
import com.persianai.assistant.services.UnifiedVoiceEngine
import com.persianai.assistant.utils.SystemIntegrationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class VoiceCallActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var recordButton: Button
    private lateinit var cancelButton: Button

    private data class ContactPhone(
        val contactId: String,
        val displayName: String,
        val phoneNumber: String
    )

    private data class ContactMatch(
        val displayName: String,
        val score: Int,
        val numbers: List<String>
    )

    private val permReqCode = 9011
    private val requiredPerms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)
        recordButton = findViewById(R.id.recordButton)
        cancelButton = findViewById(R.id.cancelButton)

        cancelButton.setOnClickListener { finish() }
        recordButton.setOnClickListener {
            if (!ensurePermissions()) return@setOnClickListener
            startVoiceCallFlow()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permReqCode) return

        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            startVoiceCallFlow()
        } else {
            statusText.text = "❌ برای تماس صوتی، مجوز میکروفن و مخاطبین لازم است"
        }
    }

    private fun ensurePermissions(): Boolean {
        val missing = requiredPerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true

        ActivityCompat.requestPermissions(this, missing.toTypedArray(), permReqCode)
        return false
    }

    private fun startVoiceCallFlow() {
        recordButton.isEnabled = false
        cancelButton.isEnabled = false
        statusText.text = "🎤 ضبط فرمان تماس..."
        transcriptText.text = ""

        lifecycleScope.launch {
            val engine = UnifiedVoiceEngine(this@VoiceCallActivity)

            if (!engine.hasRequiredPermissions()) {
                statusText.text = "❌ مجوز میکروفن لازم است"
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
                return@launch
            }

            val recording = recordWithVad(engine)
            if (recording == null) {
                statusText.text = "⚠️ چیزی شنیده نشد"
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
                return@launch
            }

            statusText.text = "📝 تبدیل گفتار به متن..."
            val analysis = engine.analyzeHybrid(recording.file)

            try { recording.file.delete() } catch (_: Exception) {}

            val text = analysis.getOrNull()?.primaryText?.trim().orEmpty()
            transcriptText.text = text

            if (text.isBlank()) {
                statusText.text = "⚠️ متن تشخیص داده نشد"
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
                return@launch
            }

            statusText.text = "📇 در حال جستجوی مخاطب..."
            handleTranscriptForCall(text)
        }
    }

    private suspend fun recordWithVad(engine: UnifiedVoiceEngine): RecordingResult? {
        return try {
            val start = engine.startRecording()
            if (start.isFailure) return null

            val startTime = System.currentTimeMillis()
            var hasSpeech = false
            var lastSpeechTime = 0L
            val maxTotalMs = 8_000L
            val maxWaitForSpeechMs = 3_500L
            val silenceStopMs = 1_000L
            val threshold = 900

            while (engine.isRecordingInProgress()) {
                val now = System.currentTimeMillis()
                val amp = engine.getCurrentAmplitude()
                if (amp > threshold) {
                    hasSpeech = true
                    lastSpeechTime = now
                }

                val total = now - startTime
                if (!hasSpeech && total > maxWaitForSpeechMs) break
                if (hasSpeech && (now - lastSpeechTime) > silenceStopMs) break
                if (total > maxTotalMs) break

                delay(120)
            }

            val stop = engine.stopRecording()
            stop.getOrNull()
        } catch (_: Exception) {
            try { engine.cancelRecording() } catch (_: Exception) {}
            null
        }
    }

    private fun handleTranscriptForCall(text: String) {
        val contactQuery = extractContactName(text)
        if (contactQuery.isBlank()) {
            statusText.text = "⚠️ نام مخاطب مشخص نشد"
            recordButton.isEnabled = true
            cancelButton.isEnabled = true
            AlertDialog.Builder(this)
                .setTitle("نام مخاطب")
                .setMessage("نام مخاطب از فرمان تشخیص داده نشد. مثال: \"با علی تماس بگیر\"")
                .setPositiveButton("باشه", null)
                .show()
            return
        }

        lifecycleScope.launch {
            try {
                statusText.text = "📇 جستجو: $contactQuery"
                val candidates = withContext(Dispatchers.IO) { queryPhoneContacts() }
                val matches = fuzzyMatchContacts(contactQuery, candidates)

                if (matches.isEmpty()) {
                    statusText.text = "⚠️ مخاطب پیدا نشد"
                    recordButton.isEnabled = true
                    cancelButton.isEnabled = true
                    return@launch
                }

                showContactSelection(matches)
            } catch (e: Exception) {
                statusText.text = "❌ خطا: ${e.message}"
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
            }
        }
    }

    private fun showContactSelection(matchesRaw: List<ContactMatch>) {
        val matches = matchesRaw.sortedByDescending { it.score }.take(8)
        val items = matches.map { "${it.displayName} (${it.numbers.size})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("انتخاب مخاطب")
            .setItems(items) { _, which ->
                val picked = matches[which]
                when (picked.numbers.size) {
                    0 -> {
                        statusText.text = "⚠️ شماره‌ای برای این مخاطب ثبت نشده"
                        recordButton.isEnabled = true
                        cancelButton.isEnabled = true
                    }
                    1 -> confirmAndDial(picked.displayName, picked.numbers.first())
                    else -> showNumberSelection(picked.displayName, picked.numbers)
                }
            }
            .setNegativeButton("لغو") { _, _ ->
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
                finish()
            }
            .show()
    }

    private fun showNumberSelection(displayName: String, numbers: List<String>) {
        val items = numbers.map { normalizePhoneNumber(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("انتخاب شماره")
            .setItems(items) { _, which ->
                val picked = items.getOrNull(which).orEmpty()
                if (picked.isBlank()) {
                    recordButton.isEnabled = true
                    cancelButton.isEnabled = true
                    return@setItems
                }
                confirmAndDial(displayName, picked)
            }
            .setNegativeButton("لغو") { _, _ ->
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
            }
            .show()
    }

    private fun confirmAndDial(displayName: String, phoneNumberRaw: String) {
        val phoneNumber = normalizePhoneNumber(phoneNumberRaw)
        if (phoneNumber.isBlank()) {
            statusText.text = "⚠️ شماره نامعتبر است"
            recordButton.isEnabled = true
            cancelButton.isEnabled = true
            return
        }

        AlertDialog.Builder(this)
            .setTitle("تأیید تماس")
            .setMessage("تماس با $displayName\n$phoneNumber")
            .setPositiveButton("تماس") { _, _ ->
                statusText.text = "📞 باز کردن شماره‌گیر..."
                SystemIntegrationHelper.makePhoneCall(this, phoneNumber)
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
                finish()
            }
            .setNegativeButton("لغو") { _, _ ->
                statusText.text = "لغو شد"
                recordButton.isEnabled = true
                cancelButton.isEnabled = true
            }
            .show()
    }

    private fun fuzzyMatchContacts(query: String, candidates: List<ContactPhone>): List<ContactMatch> {
        val q = normalizeName(query)
        if (q.isBlank()) return emptyList()

        val grouped = candidates.groupBy { it.displayName }
        val matches = mutableListOf<ContactMatch>()

        for ((name, phones) in grouped) {
            val nameNorm = normalizeName(name)
            if (nameNorm.isBlank()) continue

            val score = similarityScore(q, nameNorm)
            if (score <= 0) continue

            val numbers = phones
                .map { normalizePhoneNumber(it.phoneNumber) }
                .filter { it.isNotBlank() }
                .distinct()

            matches.add(ContactMatch(displayName = name, score = score, numbers = numbers))
        }

        return matches.sortedByDescending { it.score }
    }

    private fun normalizeName(name: String): String {
        return name
            .trim()
            .lowercase()
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("[\u200C\u200F]".toRegex(), "")
            .replace("[^\p{L}\p{N}\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun similarityScore(a: String, b: String): Int {
        if (a == b) return 100
        if (b.contains(a) || a.contains(b)) return 80

        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length).coerceAtLeast(1)
        val ratio = 1.0 - (dist.toDouble() / maxLen.toDouble())
        return (ratio * 70.0).toInt().coerceIn(0, 70)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun queryPhoneContacts(): List<ContactPhone> {
        val result = mutableListOf<ContactPhone>()
        val resolver = contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getString(idIdx) else ""
                val name = if (nameIdx >= 0) c.getString(nameIdx) else ""
                val num = if (numIdx >= 0) c.getString(numIdx) else ""
                if (name.isBlank() || num.isBlank()) continue

                result.add(
                    ContactPhone(
                        contactId = id,
                        displayName = name,
                        phoneNumber = num
                    )
                )
            }
        }

        return result
    }

    private fun extractContactName(textRaw: String): String {
        val text = textRaw.trim()

        val patterns = listOf(
            "(با|به)\\s+(.+?)\\s+(تماس|زنگ)\\s*(بگیر|بزن|کن)".toRegex(),
            "(تماس|زنگ)\\s+(با|به)\\s+(.+?)$".toRegex(),
            "call\\s+(.+?)$".toRegex(RegexOption.IGNORE_CASE)
        )

        for (p in patterns) {
            val m = p.find(text) ?: continue
            val candidate = when (m.groupValues.size) {
                3 -> m.groupValues[2]
                4 -> m.groupValues[3]
                else -> m.groupValues.last()
            }
            val cleaned = candidate
                .replace("را".toRegex(), " ")
                .replace("رو".toRegex(), " ")
                .replace("لطفا".toRegex(), " ")
                .trim()
            if (cleaned.isNotBlank()) return cleaned
        }

        return ""
    }

    private fun normalizePhoneNumber(raw: String): String {
        return raw
            .replace("[\\s()-]".toRegex(), "")
            .trim()
    }
}
