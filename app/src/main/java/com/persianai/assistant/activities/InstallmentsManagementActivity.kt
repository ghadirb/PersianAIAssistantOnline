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
        
        // فیلترها بر اساس چیپ‌های موجود در layout
        binding.chipLoan.setOnClickListener { applyFilter(FilterType.ALL) }
        binding.chipPurchase.setOnClickListener { applyFilter(FilterType.ACTIVE) }
        binding.chipRent.setOnClickListener { applyFilter(FilterType.COMPLETED) }
    }
    
    private fun loadInstallments() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                
                val allInstallments = installmentManager.getAllInstallments()
                
                installments.clear()
                installments.addAll(allInstallments)
                
                applyFilter(filterType)
                
                binding.progressBar.visibility = View.GONE
                
                if (installments.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.installmentsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
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
        binding.chipLoan.isChecked = false
        binding.chipPurchase.isChecked = false
        binding.chipRent.isChecked = false
        
        val allInstallments = installmentManager.getAllInstallments()
        val today = System.currentTimeMillis()
        val sevenDaysLater = today + (7 * 24 * 60 * 60 * 1000)
        
        val filtered = when (type) {
            FilterType.ALL -> {
                binding.chipLoan.isChecked = true
                allInstallments
            }
            FilterType.ACTIVE -> {
                binding.chipPurchase.isChecked = true
                allInstallments.filter { it.paidInstallments < it.totalInstallments }
            }
            FilterType.COMPLETED -> {
                binding.chipRent.isChecked = true
                allInstallments.filter { it.paidInstallments >= it.totalInstallments }
            }
            FilterType.OVERDUE -> {
                allInstallments.filter { 
                    it.paidInstallments < it.totalInstallments &&
                    hasOverduePayments(it, today)
                }
            }
            FilterType.UPCOMING -> {
                allInstallments.filter {
                    it.paidInstallments < it.totalInstallments &&
                    hasUpcomingPayments(it, today, sevenDaysLater)
                }
            }
        }
        
        installments.clear()
        installments.addAll(filtered)
        installmentsAdapter.notifyDataSetChanged()
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
            
            if (allInstallments.isEmpty()) {
                binding.statsCard.visibility = View.GONE
                binding.statsText.text = ""
                return@launch
            }
            
            binding.statsCard.visibility = View.VISIBLE
            
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
            
            val progressPercent = if (totalAmount > 0) {
                ((totalPaid / totalAmount) * 100).toInt()
            } else {
                0
            }
            
            binding.statsText.text = buildString {
                appendLine("تعداد اقساط: $totalInstallments")
                appendLine("مبلغ کل: ${formatAmount(totalAmount)}")
                appendLine("پرداخت شده: ${formatAmount(totalPaid)}")
                appendLine("باقیمانده: ${formatAmount(totalRemaining)}")
                appendLine("پیشرفت: $progressPercent%")
                appendLine("وضعیت: فعال $activeCount | تکمیل $completedCount")
                if (overdueCount > 0 || upcomingCount > 0) {
                    appendLine()
                    if (overdueCount > 0) {
                        appendLine("❌ اقساط عقب افتاده: $overdueCount")
                    }
                    if (upcomingCount > 0) {
                        appendLine("⚠️ اقساط تا ۷ روز آینده: $upcomingCount")
                    }
                }
            }
        }
    }
    
    private fun showAddInstallmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_installment, null)
        
        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.titleInput)
        val totalAmountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.totalAmountInput)
        val installmentAmountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.installmentAmountInput)
        val totalInstallmentsInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.totalInstallmentsInput)
        val startDateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.startDateButton)
        val paymentDayInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.paymentDayInput)
        val recipientInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.recipientInput)
        val descriptionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.descriptionInput)
        
        var selectedStartDate: Long = System.currentTimeMillis()
        
        // انتخاب تاریخ شروع
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
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val totalAmount = totalAmountInput.text?.toString()?.toLongOrNull() ?: 0L
                val totalInstallments = totalInstallmentsInput.text?.toString()?.toIntOrNull() ?: 0
                val manualInstallmentAmount = installmentAmountInput.text?.toString()?.toLongOrNull()
                val paymentDay = paymentDayInput.text?.toString()?.toIntOrNull() ?: -1
                val recipient = recipientInput.text?.toString()?.trim().orEmpty()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                
                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (totalAmount <= 0) {
                    Toast.makeText(this, "⚠️ مبلغ کل را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (totalInstallments <= 0) {
                    Toast.makeText(this, "⚠️ تعداد اقساط را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (paymentDay !in 1..31) {
                    Toast.makeText(this, "⚠️ روز پرداخت را بین ۱ تا ۳۱ وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val installmentAmount = manualInstallmentAmount
                    ?: (totalAmount / totalInstallments).takeIf { it > 0 }
                    ?: 0L
                
                if (installmentAmount <= 0) {
                    Toast.makeText(this, "⚠️ مبلغ هر قسط را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                addInstallment(
                    title = title,
                    totalAmount = totalAmount.toDouble(),
                    installmentAmount = installmentAmount.toDouble(),
                    totalInstallments = totalInstallments,
                    startDate = selectedStartDate,
                    paymentDay = paymentDay,
                    creditor = recipient,
                    notes = description
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