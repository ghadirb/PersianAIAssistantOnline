package com.persianai.assistant.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.adapters.ChecksAdapter
import com.persianai.assistant.adapters.InstallmentsAdapter
import com.persianai.assistant.databinding.ActivityFinanceAdvancedBinding
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.FinanceRuleEngine
import com.persianai.assistant.finance.InstallmentManager
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.utils.NotificationHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * صفحه پیشرفته مدیریت مالی
 * شامل: چک‌ها، اقساط، هشدارهای هوشمند
 */
class FinanceAdvancedActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFinanceAdvancedBinding
    private lateinit var checkManager: CheckManager
    private lateinit var installmentManager: InstallmentManager
    private lateinit var financeRuleEngine: FinanceRuleEngine
    
    private lateinit var checksAdapter: ChecksAdapter
    private lateinit var installmentsAdapter: InstallmentsAdapter
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
    private val numberFormat = NumberFormat.getInstance(Locale("fa", "IR"))
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinanceAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeManagers()
        setupUI()
        loadData()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💰 مدیریت مالی پیشرفته"
    }
    
    private fun initializeManagers() {
        checkManager = CheckManager(this)
        installmentManager = InstallmentManager(this)
        financeRuleEngine = FinanceRuleEngine(this)
        NotificationHelper.createNotificationChannels(this)
    }
    
    private fun setupUI() {
        // تب‌ها
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("چک‌ها"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("اقساط"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("خلاصه"))
        
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showChecksTab()
                    1 -> showInstallmentsTab()
                    2 -> showSummaryTab()
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        
        // دکمه افزودن چک
        binding.addCheckButton.setOnClickListener {
            showAddCheckDialog()
        }
        
        // دکمه افزودن قسط
        binding.addInstallmentButton.setOnClickListener {
            showAddInstallmentDialog()
        }

        binding.aiChatFab.setOnClickListener {
            showAIChatDialog()
        }
        
        // RecyclerView چک‌ها
        checksAdapter = ChecksAdapter(emptyList()) { check ->
            showCheckDetails(check)
        }
        
        binding.checksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.checksRecyclerView.adapter = checksAdapter
        
        // RecyclerView اقساط
        installmentsAdapter = InstallmentsAdapter(emptyList()) { installment ->
            showInstallmentDetails(installment)
        }
        
        binding.installmentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.installmentsRecyclerView.adapter = installmentsAdapter
    }
    
    private fun loadData() {
        lifecycleScope.launch {
            try {
                // بارگذاری چک‌ها
                val checks = checkManager.getAllChecks()
                
                // بارگذاری اقساط
                val installments = installmentManager.getAllInstallments()
                
                // به‌روزرسانی خلاصه
                updateSummary()

                // ارزیابی Rule Engine
                val ruleResult = financeRuleEngine.evaluate(14)
                updateRuleBasedCard(ruleResult)
                maybeNotifyCritical(ruleResult)
            } catch (e: Exception) {
                Toast.makeText(this@FinanceAdvancedActivity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateRuleBasedCard(result: FinanceRuleEngine.EvaluationResult) {
        val alertsText = if (result.alerts.isNotEmpty()) {
            result.alerts.joinToString(separator = "\n") { alert ->
                val icon = when (alert.severity) {
                    FinanceRuleEngine.Severity.CRITICAL -> "❗"
                    FinanceRuleEngine.Severity.WARNING -> "⚠️"
                    FinanceRuleEngine.Severity.INFO -> "ℹ️"
                }
                "$icon ${alert.title}\n${alert.description}"
            }
        } else {
            "✅ هیچ هشدار بحرانی ثبت نشده است"
        }

        val recommendationsText = if (result.recommendations.isNotEmpty()) {
            result.recommendations.joinToString(separator = "\n") { "• $it" }
        } else {
            ""
        }

        binding.financeAlertsText.text = alertsText
        binding.financeRecommendationsText.text = recommendationsText
    }

    private fun maybeNotifyCritical(result: FinanceRuleEngine.EvaluationResult) {
        val criticalAlerts = result.alerts.filter { it.severity == FinanceRuleEngine.Severity.CRITICAL }
        if (criticalAlerts.isEmpty()) return
        val summary = criticalAlerts.joinToString(separator = "\n") { "${it.title}: ${it.description}" }
        NotificationHelper.showGeneralNotification(
            this,
            "هشدار فوری مالی",
            summary,
            notificationId = 3100
        )
    }
    
    private fun showChecksTab() {
        binding.checksContainer.visibility = View.VISIBLE
        binding.installmentsContainer.visibility = View.GONE
        binding.summaryContainer.visibility = View.GONE
        binding.addCheckButton.visibility = View.VISIBLE
        binding.addInstallmentButton.visibility = View.GONE
    }
    
    private fun showInstallmentsTab() {
        binding.checksContainer.visibility = View.GONE
        binding.installmentsContainer.visibility = View.VISIBLE
        binding.summaryContainer.visibility = View.GONE
        binding.addCheckButton.visibility = View.GONE
        binding.addInstallmentButton.visibility = View.VISIBLE
    }
    
    private fun showSummaryTab() {
        binding.checksContainer.visibility = View.GONE
        binding.installmentsContainer.visibility = View.GONE
        binding.summaryContainer.visibility = View.VISIBLE
        binding.addCheckButton.visibility = View.GONE
        binding.addInstallmentButton.visibility = View.GONE
    }
    
    private fun showAddCheckDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_check, null)
        
        val checkNumberInput = dialogView.findViewById<TextInputEditText>(R.id.checkNumberInput)
        val amountInput = dialogView.findViewById<TextInputEditText>(R.id.amountInput)
        val issuerInput = dialogView.findViewById<TextInputEditText>(R.id.issuerInput)
        val recipientInput = dialogView.findViewById<TextInputEditText>(R.id.recipientInput)
        val bankNameInput = dialogView.findViewById<TextInputEditText>(R.id.bankNameInput)
        val accountNumberInput = dialogView.findViewById<TextInputEditText>(R.id.accountNumberInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        
        var issueDate = System.currentTimeMillis()
        var dueDate = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L) // یک ماه بعد
        
        val issueDateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.issueDateButton)
        val dueDateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dueDateButton)
        
        issueDateButton.text = dateFormat.format(Date(issueDate))
        dueDateButton.text = dateFormat.format(Date(dueDate))
        
        issueDateButton.setOnClickListener {
            showDatePicker { selectedDate ->
                issueDate = selectedDate
                issueDateButton.text = dateFormat.format(Date(issueDate))
            }
        }
        
        dueDateButton.setOnClickListener {
            showDatePicker { selectedDate ->
                dueDate = selectedDate
                dueDateButton.text = dateFormat.format(Date(dueDate))
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ افزودن چک جدید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val checkNumber = checkNumberInput.text.toString()
                val amount = amountInput.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                val issuer = issuerInput.text.toString()
                val recipient = recipientInput.text.toString()
                val bankName = bankNameInput.text.toString()
                val accountNumber = accountNumberInput.text.toString()
                val description = descriptionInput.text.toString()
                
                if (checkNumber.isEmpty() || amount == 0.0 || issuer.isEmpty()) {
                    Toast.makeText(this, "⚠️ لطفاً فیلدهای ضروری را پر کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                checkManager.addCheck(
                    checkNumber, amount, issuer, recipient,
                    issueDate, dueDate, bankName, accountNumber, description
                )
                
                loadData()
                Toast.makeText(this, "✅ چک با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showAddInstallmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_installment, null)
        
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.titleInput)
        val totalAmountInput = dialogView.findViewById<TextInputEditText>(R.id.totalAmountInput)
        val installmentAmountInput = dialogView.findViewById<TextInputEditText>(R.id.installmentAmountInput)
        val totalInstallmentsInput = dialogView.findViewById<TextInputEditText>(R.id.totalInstallmentsInput)
        val paymentDayInput = dialogView.findViewById<TextInputEditText>(R.id.paymentDayInput)
        val recipientInput = dialogView.findViewById<TextInputEditText>(R.id.recipientInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        
        var startDate = System.currentTimeMillis()
        
        val startDateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.startDateButton)
        startDateButton.text = dateFormat.format(Date(startDate))
        
        startDateButton.setOnClickListener {
            showDatePicker { selectedDate ->
                startDate = selectedDate
                startDateButton.text = dateFormat.format(Date(startDate))
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ افزودن قسط جدید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString()
                val totalAmount = totalAmountInput.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                val installmentAmount = installmentAmountInput.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
                val totalInstallments = totalInstallmentsInput.text.toString().toIntOrNull() ?: 0
                val paymentDay = paymentDayInput.text.toString().toIntOrNull() ?: 1
                val recipient = recipientInput.text.toString()
                val description = descriptionInput.text.toString()
                
                if (title.isEmpty() || totalAmount == 0.0 || installmentAmount == 0.0 || totalInstallments == 0) {
                    Toast.makeText(this, "⚠️ لطفاً فیلدهای ضروری را پر کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                installmentManager.addInstallment(
                    title, totalAmount, installmentAmount, totalInstallments,
                    startDate, paymentDay, recipient, description
                )
                
                loadData()
                Toast.makeText(this, "✅ قسط با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("تاریخ را انتخاب کنید")
            .setSelection(System.currentTimeMillis())
            .build()
        
        datePicker.addOnPositiveButtonClickListener { selection ->
            onDateSelected(selection)
        }
        
        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }
    
    private fun showCheckDetails(check: CheckManager.Check) {
        val message = buildString {
            appendLine("شماره چک: ${check.checkNumber}")
            appendLine("مبلغ: ${numberFormat.format(check.amount)} ریال")
            appendLine("صادرکننده: ${check.issuer}")
            appendLine("دریافت‌کننده: ${check.recipient}")
            appendLine("تاریخ صدور: ${dateFormat.format(Date(check.issueDate))}")
            appendLine("تاریخ سررسید: ${dateFormat.format(Date(check.dueDate))}")
            appendLine("بانک: ${check.bankName}")
            appendLine("شماره حساب: ${check.accountNumber}")
            appendLine("وضعیت: ${check.status.name}")
            if (check.description.isNotEmpty()) {
                appendLine("توضیحات: ${check.description}")
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("💵 جزئیات چک")
            .setMessage(message)
            .setPositiveButton("بستن", null)
            .show()
    }
    
    private fun showInstallmentDetails(installment: InstallmentManager.Installment) {
        val remainingInstallments = installment.totalInstallments - installment.paidInstallments
        val remainingAmount = remainingInstallments * installment.installmentAmount
        val nextPaymentDate = installmentManager.calculateNextPaymentDate(installment)
        
        val message = buildString {
            appendLine("عنوان: ${installment.title}")
            appendLine("مبلغ کل: ${numberFormat.format(installment.totalAmount)} ریال")
            appendLine("مبلغ هر قسط: ${numberFormat.format(installment.installmentAmount)} ریال")
            appendLine("تعداد کل اقساط: ${installment.totalInstallments}")
            appendLine("اقساط پرداخت‌شده: ${installment.paidInstallments}")
            appendLine("اقساط باقیمانده: $remainingInstallments")
            appendLine("مبلغ باقیمانده: ${numberFormat.format(remainingAmount)} ریال")
            appendLine("روز پرداخت: ${installment.paymentDay}")
            appendLine("دریافت‌کننده: ${installment.recipient}")
            if (nextPaymentDate != null) {
                appendLine("تاریخ قسط بعدی: ${dateFormat.format(Date(nextPaymentDate))}")
            }
            if (installment.description.isNotEmpty()) {
                appendLine("توضیحات: ${installment.description}")
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("📋 جزئیات قسط")
            .setMessage(message)
            .setPositiveButton("بستن", null)
            .show()
    }
    
    private fun updateSummary() {
        // چک‌ها
        val totalChecksAmount = checkManager.getTotalPendingAmount()
        val upcomingChecks = checkManager.getUpcomingChecks(30)
        
        binding.totalChecksText.text = "${numberFormat.format(totalChecksAmount)} ریال"
        binding.upcomingChecksCount.text = "${upcomingChecks.size} چک"
        
        // اقساط
        val totalInstallmentsAmount = installmentManager.getTotalRemainingAmount()
        val upcomingInstallments = installmentManager.getUpcomingPayments(30)
        
        binding.totalInstallmentsText.text = "${numberFormat.format(totalInstallmentsAmount)} ریال"
        binding.upcomingInstallmentsCount.text = "${upcomingInstallments.size} قسط"
        
        // کل
        val totalAmount = totalChecksAmount + totalInstallmentsAmount
        binding.totalAmountText.text = "${numberFormat.format(totalAmount)} ریال"
    }
    
    private fun checkAlerts() {
        // Alerts checking disabled for now
    }
    
    private fun showAIChatDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "مثال: مجموع چک‌های این ماه چقدره؟"
            setPadding(32, 32, 32, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 چت هوشمند مالی")
            .setView(input)
            .setPositiveButton("اجرا") { _, _ ->
                val userMessage = input.text.toString().trim()
                if (userMessage.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val assistant = AdvancedPersianAssistant(this@FinanceAdvancedActivity)
                            val response = assistant.processRequestWithAI(
                                userMessage,
                                contextHint = "سوالات مالی، چک‌ها، اقساط، گزارش مالی"
                            )

                            MaterialAlertDialogBuilder(this@FinanceAdvancedActivity)
                                .setTitle("پاسخ دستیار")
                                .setMessage(response.text)
                                .setPositiveButton("باشه") { _, _ ->
                                    when (response.actionType) {
                                        AdvancedPersianAssistant.ActionType.OPEN_CHECKS ->
                                            binding.tabLayout.getTabAt(0)?.select()
                                        AdvancedPersianAssistant.ActionType.OPEN_INSTALLMENTS ->
                                            binding.tabLayout.getTabAt(1)?.select()
                                        else -> {}
                                    }
                                }
                                .show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@FinanceAdvancedActivity,
                                "خطا: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
