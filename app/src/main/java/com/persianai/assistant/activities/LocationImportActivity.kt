package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.navigation.LocationShareParser
import com.persianai.assistant.navigation.SavedLocationsManager

/**
 * پذیرش لینک‌های اشتراکی (نشان/گوگل‌مپ) و ذخیره در مکان‌های ذخیره‌شده
 */
class LocationImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_SEND || intent?.type?.startsWith("text/") != true) {
            finish()
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val parsed = LocationShareParser.parse(sharedText)
        if (parsed == null) {
            Toast.makeText(this, "مختصات قابل تشخیص نیست", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val source = when {
            sharedText.contains("neshan", ignoreCase = true) -> "neshan"
            sharedText.contains("google", ignoreCase = true) -> "gmaps"
            else -> "shared"
        }

        val defaultName = parsed.nameHint ?: "مقصد اشتراکی"
        val input = TextInputEditText(this).apply {
            setText(defaultName)
            hint = "نام مقصد را بنویسید"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("ذخیره مقصد اشتراکی")
            .setMessage("مختصات: ${parsed.latLng.latitude}, ${parsed.latLng.longitude}\nمنبع: $source")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = input.text?.toString()?.ifBlank { defaultName } ?: defaultName
                val manager = SavedLocationsManager(this)
                val ok = manager.upsertLocation(
                    name = name,
                    address = parsed.raw.take(120),
                    latLng = parsed.latLng,
                    category = "favorite",
                    source = source
                )
                Toast.makeText(this, if (ok) "ذخیره شد" else "خطا در ذخیره", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("انصراف") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
