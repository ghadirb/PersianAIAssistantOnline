package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.persianai.assistant.adapter.FinancePagerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.ai.AIFinanceProcessor
import com.persianai.assistant.databinding.ActivityFinanceAdvancedBinding
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.InstallmentManager
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.launch

class FinanceAdvancedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFinanceAdvancedBinding
    private lateinit var aiFinanceProcessor: AIFinanceProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinanceAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "مدیریت مالی پیشرفته"

        val prefsManager = PreferencesManager(this)
        val aiClient = AIClient(prefsManager.getAPIKeys())
        val checkManager = CheckManager(this)
        val installmentManager = InstallmentManager(this)
        aiFinanceProcessor = AIFinanceProcessor(aiClient, checkManager, installmentManager)

        setupViewPager()
        setupFab()
    }

    private fun setupFab() {
        binding.aiChatFab.setOnClickListener {
            showAIChatDialog()
        }
    }

    private fun showAIChatDialog() {
        val input = TextInputEditText(this)
        input.hint = "دستور خود را وارد کنید..."

        MaterialAlertDialogBuilder(this)
            .setTitle("چت هوشمند مالی")
            .setView(input)
            .setPositiveButton("ارسال") { _, _ ->
                val command = input.text.toString()
                if (command.isNotBlank()) {
                    lifecycleScope.launch {
                        val result = aiFinanceProcessor.processCommand(command)
                        MaterialAlertDialogBuilder(this@FinanceAdvancedActivity)
                            .setMessage(result)
                            .setPositiveButton("باشه", null)
                            .show()
                        // TODO: Reload data in the currently visible fragment
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun setupViewPager() {
        val pagerAdapter = FinancePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
