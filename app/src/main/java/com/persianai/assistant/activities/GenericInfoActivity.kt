package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityGenericInfoBinding

class GenericInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGenericInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "بخش اطلاعات"
        val desc = intent.getStringExtra(EXTRA_DESC) ?: "جزئیات در دسترس نیست"
        val preset = intent.getStringExtra(EXTRA_PRESET)

        binding.toolbar.title = title
        binding.titleText.text = title
        binding.descText.text = desc

        binding.startChatButton.setOnClickListener {
            val intentChat = Intent(this, AIChatActivity::class.java)
            if (!preset.isNullOrBlank()) {
                intentChat.putExtra("presetMessage", preset)
            }
            startActivity(intentChat)
        }

        binding.closeButton.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
        const val EXTRA_PRESET = "extra_preset"
    }
}
