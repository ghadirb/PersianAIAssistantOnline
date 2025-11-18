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
import com.persianai.assistant.databinding.ActivityInstallmentsManagementBinding
import com.persianai.assistant.finance.InstallmentManager
import com.persianai.assistant.finance.InstallmentManager.Installment
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
        installmentsAdapter = InstallmentsAdapter(installments) { installment ->
            viewInstallmentDetails(installment)
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
                allInstallments.filter { it.paidInstallments < it.totalInstallments }
            }
            FilterType.COMPLETED -> {
                binding.chipCompleted.isChecked = true
                allInstallments.filter { it.paidInstallments >= it.totalInstallments }
            }
            FilterType.OVERDUE -> {
                binding.chipOverdue.isChecked = true
                allInstallments.filter { 
                    it.paidInstallments < it.totalInstallments &&
                    hasOverduePayments(it, today)
                }
            }
            FilterType.UPCOMING -> {
                binding.chipUpcoming.isChecked = true
                allInstallments.filter {
                    it.paidInstallments < it.totalInstallments &&
                    hasUpcomingPayments(it, today, sevenDaysLater)
                }
            }
        }
        
        installments.clear()
        installments.addAll(filtered)
        installmentsAdapter.notifyDataSetChanged()
        
        binding.installmentsCountText.text = "تعداد: ${installments.size}"
    }
    
    private fun hasOverduePayments(installment: Installment, now: Long = System.currentTimeMillis()): Boolean {
        val nextPaymentDate = installmentManager.calculateNextPaymentDate(installment) ?: return false
        return nextPaymentDate < now
    }
    
    private fun hasUpcomingPayments(installment: Installment, start: Long, end: Long): Boolean {
        val nextPaymentDate = installmentManager.calculateNextPaymentDate(installment) ?: return false
        return nextPaymentDate in start..end
    }
    
    private fun updateStats() {
        lifecycleScope.launch {
            val allInstallments = installmentManager.getAllInstallments()
            
            binding.statsCard.visibility = if (allInstallments.isEmpty()) View.GONE else View.VISIBLE
            
            val totalInstallments = allInstallments.size
            val totalAmount = allInstallments.sumOf { it.totalAmount }
            val totalPaid = allInstallments.sumOf { it.paidInstallments * it.installmentAmount }
            val totalRemaining = allInstallments.sumOf { (it.totalInstallments - it.paidInstallments) * it.installmentAmount }
            
            val activeCount = allInstallments.count { it.paidInstallments < it.totalInstallments }
            val completedCount = allInstallments.count { it.paidInstallments >= it.totalInstallments }
            
            val today = System.currentTimeMillis()
            val sevenDaysLater = today + (7 * 24 * 60 * 60 * 1000)
            val overdueCount = allInstallments.count { hasOverduePayments(it, today) }
            val upcomingCount = allInstallments.count { hasUpcomingPayments(it, today, sevenDaysLater) }
            
            binding.totalInstallmentsText.text = "${totalInstallments} قسط"
            binding.totalAmountText.text = formatAmount(totalAmount)
            binding.totalPaidText.text = formatAmount(totalPaid)
            binding.totalRemainingText.text = formatAmount(totalRemaining)
            
            binding.activeCountText.text = "${activeCount} فعال"
            binding.completedCountText.text = "${completedCount} تکمیل"
            binding.overdueCountText.text = "${overdueCount} عقب افتاده"
            
            // Progress bar
            val progress = if (totalAmount > 0) {
                ((totalPaid / totalAmount) * 100).toInt()
            } else {
                0
            }
            binding.paymentProgressBar.progress = progress
            binding.progressText.text = "$progress%"
            
            // هشدار
            if (overdueCount > 0 || upcomingCount > 0) {
                binding.alertCard.visibility = View.VISIBLE
                binding.alertText.text = buildString {
                    if (overdueCount > 0) {
                        append("❌ ${overdueCount} قسط عقب افتاده")
                    }
                    if (upcomingCount > 0) {
                        if (overdueCount > 0) append("\n")
                        append("⚠️ ${upcomingCount} قسط تا 7 روز آینده")
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
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = selection
                }
                val persianDate = PersianDateConverter.gregorianToPersian(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                startDateButton.text = persianDate.toReadableString()
            }
            
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ افزودن قسط جدید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
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
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = selectedStartDate
                }
                val paymentDay = calendar.get(Calendar.DAY_OF_MONTH)
                
                addInstallment(
                    title = title,
                    totalAmount = totalAmount.toDouble(),
                    installmentAmount = amountPerInstallment.toDouble(),
                    totalInstallments = installmentCount,
                    startDate = selectedStartDate,
                    paymentDay = paymentDay,
                    creditor = creditor,
                    notes = notes
                )
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun addInstallment(
        title: String,
        totalAmount: Double,
        installmentAmount: Double,
        totalInstallments: Int,
        startDate: Long,
        paymentDay: Int,
        creditor: String,
        notes: String
    ) {
        lifecycleScope.launch {
            try {
                // ثبت هشدارها برای پرداخت‌ها
                installmentManager.addInstallment(
                    title = title,
                    totalAmount = totalAmount,
                    installmentAmount = installmentAmount,
                    totalInstallments = totalInstallments,
                    startDate = startDate,
                    paymentDay = paymentDay,
                    recipient = creditor,
                    description = notes
                )
                
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
        val statusText = if (installment.paidInstallments >= installment.totalInstallments) {
            "✔️ تکمیل"
        } else {
            "✅ فعال"
        }
        
        val remainingInstallments = installment.totalInstallments - installment.paidInstallments
        val remainingAmount = remainingInstallments * installment.installmentAmount
        val progressPercent = if (installment.totalInstallments > 0) {
            ((installment.paidInstallments.toDouble() / installment.totalInstallments) * 100).toInt()
        } else {
            0
        }
        
        val details = buildString {
            appendLine("عنوان: ${installment.title}")
            appendLine("مبلغ کل: ${formatAmount(installment.totalAmount)}")
            appendLine("مبلغ هر قسط: ${formatAmount(installment.installmentAmount)}")
            appendLine("تعداد اقساط: ${installment.totalInstallments}")
            appendLine("پرداخت شده: ${installment.paidInstallments} قسط")
            appendLine("باقیمانده: ${remainingInstallments} قسط")
            appendLine("مبلغ باقیمانده: ${formatAmount(remainingAmount)}")
            appendLine("پیشرفت: $progressPercent%")
            appendLine("وضعیت: $statusText")
            if (installment.recipient.isNotEmpty()) {
                appendLine("طلبکار: ${installment.recipient}")
            }
            if (installment.description.isNotEmpty()) {
                appendLine("\nیادداشت:")
                appendLine(installment.description)
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
            
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            
            for (i in 1..installment.totalInstallments) {
                calendar.timeInMillis = installment.startDate
                calendar.add(Calendar.MONTH, i - 1)
                calendar.set(Calendar.DAY_OF_MONTH, installment.paymentDay)
                val dueTime = calendar.timeInMillis
                val dueCal = Calendar.getInstance().apply {
                    timeInMillis = dueTime
                }
                val persianDate = PersianDateConverter.gregorianToPersian(
                    dueCal.get(Calendar.YEAR),
                    dueCal.get(Calendar.MONTH) + 1,
                    dueCal.get(Calendar.DAY_OF_MONTH)
                )
                val isPaid = i <= installment.paidInstallments
                val status = when {
                    isPaid -> "✅ پرداخت شده"
                    dueTime < now -> "❌ عقب افتاده"
                    else -> "⏳ در انتظار"
                }
                
                appendLine("قسط ${i}:")
                appendLine("  مبلغ: ${formatAmount(installment.installmentAmount)}")
                appendLine("  سررسید: ${persianDate.toReadableString()}")
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
        // TODO: پیاده‌سازی ثبت پرداخت قسط با ساختار جدید اقساط
        Toast.makeText(this, "ثبت پرداخت قسط در نسخه فعلی در حال توسعه است.", Toast.LENGTH_SHORT).show()
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
    
    private fun formatAmount(amount: Double): String {
        return String.format("%,.0f تومان", amount)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // منوی اختصاصی اقساط در حال حاضر غیرفعال است
        return super.onCreateOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
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