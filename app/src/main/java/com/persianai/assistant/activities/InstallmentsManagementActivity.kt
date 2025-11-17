package com.persianai.assistant.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.adapters.InstallmentsAdapter
import com.persianai.assistant.data.Installment
import com.persianai.assistant.databinding.ActivityInstallmentsManagementBinding
import com.persianai.assistant.finance.InstallmentManager
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.launch
import java.util.*

/**
 * ماژول مدیریت جامع اقساط
 * 
 * ✅ ثبت قسط (وام، خرید، اجاره)
 * ✅ تعداد اقساط
 * ✅ جدول زمان‌بندی
 * ✅ هشدارهای هوشمند
 * ✅ محاسبه بدهی باقیمانده
 */
class InstallmentsManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallmentsManagementBinding
    private lateinit var installmentsAdapter: InstallmentsAdapter
    private lateinit var installmentManager: InstallmentManager
    private val installments = mutableListOf<Installment>()
    
    private var filterType: FilterType = FilterType.ALL
    
    enum class FilterType {
        ALL,           // همه
        ACTIVE,        // فعال
        COMPLETED,     // تکمیل شده
        OVERDUE,       // عقب افتاده
        UPCOMING       // سررسید نزدیک
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallmentsManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeManager()
        setupRecyclerView()
        setupListeners()
        loadInstallments()
        updateStats()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💳 مدیریت اقساط"
    }
    
    private fun initializeManager() {
        installmentManager = InstallmentManager(this)
    }
    
    private fun setupRecyclerView() {
        installmentsAdapter = InstallmentsAdapter(installments) { installment, action ->
            when (action) {
                "view" -> viewInstallmentDetails(installment)
                "pay" -> markPaymentPaid(installment)
                "schedule" -> viewPaymentSchedule(installment)
                "edit" -> editInstallment(installment)
                "delete" -> deleteInstallment(installment)
            }
        }
        
        binding.installmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@InstallmentsManagementActivity)
            adapter = installmentsAdapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddInstallment.setOnClickListener {
            showAddInstallmentDialog()
        }
        
        // Filters
        binding.chipAll.setOnClickListener { applyFilter(FilterType.ALL) }
        binding.chipActive.setOnClickListener { applyFilter(FilterType.ACTIVE) }
        binding.chipCompleted.setOnClickListener { applyFilter(FilterType.COMPLETED) }
        binding.chipOverdue.setOnClickListener { applyFilter(FilterType.OVERDUE) }
        binding.chipUpcoming.setOnClickListener { applyFilter(FilterType.UPCOMING) }
    }
    
    private fun loadInstallments() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                
                val allInstallments = installmentManager.getAllInstallments()
                
                installments.clear()
                installments.addAll(allInstallments)
                
                applyFilter(filterType)
                
                binding.progressBar.visibility = View.GONE
                
                if (installments.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.installmentsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.installmentsRecyclerView.visibility = View.VISIBLE
                }
                
                updateStats()
                
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@InstallmentsManagementActivity,
                    "❌ خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun applyFilter(type: FilterType) {
        filterType = type
        
        // Reset chips
        binding.chipAll.isChecked = false
        binding.chipActive.isChecked = false
        binding.chipCompleted.isChecked = false
        binding.chipOverdue.isChecked = false
        binding.chipUpcoming.isChecked = false
        
        val allInstallments = installmentManager.getAllInstallments()
        val today = System.currentTimeMillis()
        val sevenDaysLater = today + (7 * 24 * 60 * 60 * 1000)
        
        val filtered = when (type) {
            FilterType.ALL -> {
                binding.chipAll.isChecked = true
                allInstallments
            }
            FilterType.ACTIVE -> {
                binding.chipActive.isChecked = true
                allInstallments.filter { it.status == Installment.InstallmentStatus.ACTIVE }
            }
            FilterType.COMPLETED -> {
                binding.chipCompleted.isChecked = true
                allInstallments.filter { it.status == Installment.InstallmentStatus.COMPLETED }
            }
            FilterType.OVERDUE -> {
                binding.chipOverdue.isChecked = true
                allInstallments.filter { 
                    it.status == Installment.InstallmentStatus.ACTIVE &&
                    hasOverduePayments(it)
                }
            }
            FilterType.UPCOMING -> {
                binding.chipUpcoming.isChecked = true
                allInstallments.filter {
                    it.status == Installment.InstallmentStatus.ACTIVE &&
                    hasUpcomingPayments(it, today, sevenDaysLater)
                }
            }
        }
        
        installments.clear()
        installments.addAll(filtered)
        installmentsAdapter.notifyDataSetChanged()
        
        binding.installmentsCountText.text = "تعداد: ${installments.size}"
    }
    
    private fun hasOverduePayments(installment: Installment): Boolean {
        val today = System.currentTimeMillis()
        return installment.payments.any { 
            !it.paid && it.dueDate < today 
        }
    }
    
    private fun hasUpcomingPayments(installment: Installment, start: Long, end: Long): Boolean {
        return installment.payments.any { 
            !it.paid && it.dueDate in start..end 
        }
    }
    
    private fun updateStats() {
        lifecycleScope.launch {
            val stats = installmentManager.getInstallmentStats()
            
            binding.statsCard.visibility = View.VISIBLE
            
            binding.totalInstallmentsText.text = "${stats.totalInstallments} قسط"
            binding.totalAmountText.text = formatAmount(stats.totalAmount)
            binding.totalPaidText.text = formatAmount(stats.totalPaid)
            binding.totalRemainingText.text = formatAmount(stats.totalRemaining)
            
            binding.activeCountText.text = "${stats.activeCount} فعال"
            binding.completedCountText.text = "${stats.completedCount} تکمیل"
            binding.overdueCountText.text = "${stats.overdueCount} عقب افتاده"
            
            // Progress bar
            val progress = if (stats.totalAmount > 0) {
                ((stats.totalPaid.toDouble() / stats.totalAmount) * 100).toInt()
            } else {
                0
            }
            binding.paymentProgressBar.progress = progress
            binding.progressText.text = "$progress%"
            
            // هشدار
            if (stats.overdueCount > 0 || stats.upcomingCount > 0) {
                binding.alertCard.visibility = View.VISIBLE
                binding.alertText.text = buildString {
                    if (stats.overdueCount > 0) {
                        append("❌ ${stats.overdueCount} قسط عقب افتاده")
                    }
                    if (stats.upcomingCount > 0) {
                        if (stats.overdueCount > 0) append("\n")
                        append("⚠️ ${stats.upcomingCount} قسط تا 7 روز آینده")
                    }
                }
            } else {
                binding.alertCard.visibility = View.GONE
            }
        }
    }
    
    private fun showAddInstallmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_installment, null)
        
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.typeSpinner)
        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.titleInput)
        val totalAmountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.totalAmountInput)
        val installmentCountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.installmentCountInput)
        val startDateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectStartDateButton)
        val intervalDaysInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.intervalDaysInput)
        val creditorInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.creditorInput)
        val notesInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.notesInput)
        
        var selectedStartDate: Long = System.currentTimeMillis()
        
        // Setup type spinner
        val types = arrayOf("وام", "خرید اقساطی", "اجاره", "سایر")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter
        
        startDateButton.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("تاریخ شروع")
                .setSelection(selectedStartDate)
                .build()
            
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedStartDate = selection
                val persianDate = PersianDateConverter.gregorianToPersian(Date(selection))
                startDateButton.text = persianDate
            }
            
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ افزودن قسط جدید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val type = when (typeSpinner.selectedItemPosition) {
                    0 -> Installment.InstallmentType.LOAN
                    1 -> Installment.InstallmentType.PURCHASE
                    2 -> Installment.InstallmentType.RENT
                    else -> Installment.InstallmentType.OTHER
                }
                
                val title = titleInput.text.toString()
                val totalAmount = totalAmountInput.text.toString().toLongOrNull() ?: 0L
                val installmentCount = installmentCountInput.text.toString().toIntOrNull() ?: 0
                val intervalDays = intervalDaysInput.text.toString().toIntOrNull() ?: 30
                val creditor = creditorInput.text.toString()
                val notes = notesInput.text.toString()
                
                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (totalAmount <= 0) {
                    Toast.makeText(this, "⚠️ مبلغ کل را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (installmentCount <= 0) {
                    Toast.makeText(this, "⚠️ تعداد اقساط را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // محاسبه مبلغ هر قسط
                val amountPerInstallment = totalAmount / installmentCount
                
                // ایجاد لیست پرداخت‌ها
                val payments = mutableListOf<Installment.Payment>()
                var currentDate = selectedStartDate
                
                for (i in 1..installmentCount) {
                    payments.add(
                        Installment.Payment(
                            id = UUID.randomUUID().toString(),
                            number = i,
                            amount = amountPerInstallment,
                            dueDate = currentDate,
                            paid = false,
                            paidDate = null
                        )
                    )
                    
                    // افزودن interval به تاریخ
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = currentDate
                    calendar.add(Calendar.DAY_OF_MONTH, intervalDays)
                    currentDate = calendar.timeInMillis
                }
                
                val installment = Installment(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = title,
                    totalAmount = totalAmount,
                    amountPerInstallment = amountPerInstallment,
                    installmentCount = installmentCount,
                    paidCount = 0,
                    startDate = selectedStartDate,
                    intervalDays = intervalDays,
                    creditor = creditor,
                    status = Installment.InstallmentStatus.ACTIVE,
                    payments = payments,
                    notes = notes,
                    createdAt = System.currentTimeMillis()
                )
                
                addInstallment(installment)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun addInstallment(installment: Installment) {
        lifecycleScope.launch {
            try {
                installmentManager.addInstallment(installment)
                
                // ثبت هشدارها برای پرداخت‌ها
                installmentManager.schedulePaymentAlerts(installment)
                
                Toast.makeText(
                    this@InstallmentsManagementActivity,
                    "✅ قسط با موفقیت ثبت شد",
                    Toast.LENGTH_SHORT
                ).show()
                
                loadInstallments()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@InstallmentsManagementActivity,
                    "❌ خطا: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun viewInstallmentDetails(installment: Installment) {
        val typeText = when (installment.type) {
            Installment.InstallmentType.LOAN -> "💰 وام"
            Installment.InstallmentType.PURCHASE -> "🛒 خرید"
            Installment.InstallmentType.RENT -> "🏠 اجاره"
            Installment.InstallmentType.OTHER -> "📋 سایر"
        }
        
        val statusText = when (installment.status) {
            Installment.InstallmentStatus.ACTIVE -> "✅ فعال"
            Installment.InstallmentStatus.COMPLETED -> "✔️ تکمیل"
            Installment.InstallmentStatus.CANCELLED -> "❌ لغو شده"
        }
        
        val remainingAmount = installment.totalAmount - (installment.paidCount * installment.amountPerInstallment)
        val progressPercent = ((installment.paidCount.toDouble() / installment.installmentCount) * 100).toInt()
        
        val details = buildString {
            appendLine("نوع: $typeText")
            appendLine("عنوان: ${installment.title}")
            appendLine("مبلغ کل: ${formatAmount(installment.totalAmount)}")
            appendLine("مبلغ هر قسط: ${formatAmount(installment.amountPerInstallment)}")
            appendLine("تعداد اقساط: ${installment.installmentCount}")
            appendLine("پرداخت شده: ${installment.paidCount} قسط")
            appendLine("باقیمانده: ${installment.installmentCount - installment.paidCount} قسط")
            appendLine("مبلغ باقیمانده: ${formatAmount(remainingAmount)}")
            appendLine("پیشرفت: $progressPercent%")
            appendLine("وضعیت: $statusText")
            if (installment.creditor.isNotEmpty()) {
                appendLine("طلبکار: ${installment.creditor}")
            }
            if (installment.notes.isNotEmpty()) {
                appendLine("\nیادداشت:")
                appendLine(installment.notes)
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("جزئیات قسط")
            .setMessage(details)
            .setPositiveButton("جدول پرداخت") { _, _ ->
                viewPaymentSchedule(installment)
            }
            .setNeutralButton("بستن", null)
            .setNegativeButton("حذف") { _, _ ->
                deleteInstallment(installment)
            }
            .show()
    }
    
    private fun viewPaymentSchedule(installment: Installment) {
        val schedule = buildString {
            appendLine("📅 جدول زمان‌بندی پرداخت")
            appendLine("================")
            appendLine()
            
            installment.payments.forEachIndexed { index, payment ->
                val persianDate = PersianDateConverter.gregorianToPersian(Date(payment.dueDate))
                val status = if (payment.paid) {
                    val paidDate = PersianDateConverter.gregorianToPersian(Date(payment.paidDate ?: 0))
                    "✅ پرداخت شده ($paidDate)"
                } else if (payment.dueDate < System.currentTimeMillis()) {
                    "❌ عقب افتاده"
                } else {
                    "⏳ در انتظار"
                }
                
                appendLine("قسط ${payment.number}:")
                appendLine("  مبلغ: ${formatAmount(payment.amount)}")
                appendLine("  سررسید: $persianDate")
                appendLine("  وضعیت: $status")
                appendLine()
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("جدول پرداخت")
            .setMessage(schedule)
            .setPositiveButton("بستن", null)
            .show()
    }
    
    private fun markPaymentPaid(installment: Installment) {
        val unpaidPayments = installment.payments.filter { !it.paid }
        
        if (unpaidPayments.isEmpty()) {
            Toast.makeText(this, "✅ همه اقساط پرداخت شده", Toast.LENGTH_SHORT).show()
            return
        }
        
        val paymentNames = unpaidPayments.map { payment ->
            val persianDate = PersianDateConverter.gregorianToPersian(Date(payment.dueDate))
            "قسط ${payment.number} - ${formatAmount(payment.amount)} - $persianDate"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب قسط برای پرداخت")
            .setItems(paymentNames) { _, which ->
                val selectedPayment = unpaidPayments[which]
                
                lifecycleScope.launch {
                    try {
                        installmentManager.markPaymentAsPaid(
                            installment.id,
                            selectedPayment.id,
                            System.currentTimeMillis()
                        )
                        
                        Toast.makeText(
                            this@InstallmentsManagementActivity,
                            "✅ پرداخت ثبت شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        loadInstallments()
                        
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@InstallmentsManagementActivity,
                            "❌ خطا: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun editInstallment(installment: Installment) {
        Toast.makeText(this, "🚧 ویرایش در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun deleteInstallment(installment: Installment) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف قسط")
            .setMessage("آیا از حذف این قسط مطمئن هستید؟\n\nتمام اطلاعات پرداخت‌ها نیز حذف خواهند شد.")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        installmentManager.deleteInstallment(installment.id)
                        
                        Toast.makeText(
                            this@InstallmentsManagementActivity,
                            "✅ قسط حذف شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        loadInstallments()
                        
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@InstallmentsManagementActivity,
                            "❌ خطا: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }
    
    private fun formatAmount(amount: Long): String {
        return String.format("%,d تومان", amount)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.installments_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export -> {
                exportInstallments()
                true
            }
            R.id.action_report -> {
                generateReport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun exportInstallments() {
        Toast.makeText(this, "🚧 اکسپورت در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun generateReport() {
        Toast.makeText(this, "🚧 گزارش در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
}