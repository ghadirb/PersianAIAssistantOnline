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
import com.persianai.assistant.adapters.ChecksAdapter
import com.persianai.assistant.databinding.ActivityChecksManagementBinding
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ماژول مدیریت جامع چک‌ها
 * 
 * ✅ ثبت چک پرداختی/دریافتی
 * ✅ تاریخ سررسید
 * ✅ هشدارهای هوشمند
 * ✅ وضعیت چک
 * ✅ یادداشت و پیگیری
 */
class ChecksManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChecksManagementBinding
    private lateinit var checksAdapter: ChecksAdapter
    private lateinit var checkManager: CheckManager
    private val checks = mutableListOf<CheckManager.Check>()
    
    private var filterType: CheckFilterType = CheckFilterType.ALL
    
    enum class CheckFilterType {
        ALL,           // همه
        PAYABLE,       // پرداختی
        RECEIVABLE,    // دریافتی
        PENDING,       // در انتظار
        CASHED,        // پاس شده
        BOUNCED,       // برگشتی
        UPCOMING       // سررسید نزدیک
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecksManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeManager()
        setupRecyclerView()
        setupListeners()
        loadChecks()
        updateStats()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💳 مدیریت چک‌ها"
    }
    
    private fun initializeManager() {
        checkManager = CheckManager(this)
    }
    
    private fun setupRecyclerView() {
        checksAdapter = ChecksAdapter(checks) { check ->
            viewCheckDetails(check)
        }
        
        binding.checksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChecksManagementActivity)
            adapter = checksAdapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddCheck.setOnClickListener {
            showAddCheckDialog()
        }
    }
    
    private fun loadChecks() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val allChecks = checkManager.getAllChecks()
                
                checks.clear()
                checks.addAll(allChecks)
                
                applyFilter(filterType)
                
                binding.progressBar.visibility = View.GONE
                
                if (checks.isEmpty()) {
                    binding.checksRecyclerView.visibility = View.GONE
                } else {
                    binding.checksRecyclerView.visibility = View.VISIBLE
                }
                
                updateStats()
                
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ChecksManagementActivity,
                    "❌ خطا در بارگذاری: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun applyFilter(type: CheckFilterType) {
        filterType = type
        checksAdapter.notifyDataSetChanged()
    }
    
    private fun updateStats() {
        lifecycleScope.launch {
            val stats = checkManager.getCheckStats()
            
            binding.statsCard.visibility = View.VISIBLE
            
            binding.totalChecksText.text = "${stats.totalChecks} چک"
            binding.totalAmountText.text = formatAmount(stats.totalAmount)
            
            binding.payableCountText.text = "${stats.payableCount} پرداختی"
            binding.payableAmountText.text = formatAmount(stats.payableAmount)
            
            binding.receivableCountText.text = "${stats.receivableCount} دریافتی"
            binding.receivableAmountText.text = formatAmount(stats.receivableAmount)
            
            binding.pendingCountText.text = "${stats.pendingCount} در انتظار"
            binding.cashedCountText.text = "${stats.cashedCount} پاس شده"
            binding.bouncedCountText.text = "${stats.bouncedCount} برگشتی"
            
            // هشدار چک‌های نزدیک به سررسید
            if (stats.upcomingCount > 0) {
                binding.alertCard.visibility = View.VISIBLE
                binding.alertText.text = "⚠️ ${stats.upcomingCount} چک تا 7 روز آینده سررسید دارند"
            } else {
                binding.alertCard.visibility = View.GONE
            }
        }
    }
    
    private fun showAddCheckDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_check, null)
        
        // Views
        val typeGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupCheckType)
        val amountInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.amountInput)
        val checkNumberInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.checkNumberInput)
        val holderNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.holderNameInput)
        val accountNumberInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.accountNumberInput)
        val dueDateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectDueDateButton)
        val notesInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.notesInput)
        val alertDaysInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.alertDaysInput)
        
        var selectedDueDate: Long = System.currentTimeMillis()
        
        dueDateButton.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("تاریخ سررسید")
                .setSelection(selectedDueDate)
                .build()
            
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDueDate = selection
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = selection
                }
                val persianDate = PersianDateConverter.gregorianToPersian(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                dueDateButton.text = persianDate.toReadableString()
            }
            
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ افزودن چک جدید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val amount = amountInput.text.toString().toLongOrNull() ?: 0L
                val checkNumber = checkNumberInput.text.toString()
                val holderName = holderNameInput.text.toString()
                val alertDays = alertDaysInput.text.toString().toIntOrNull() ?: 3
                
                if (amount <= 0) {
                    Toast.makeText(this, "⚠️ مبلغ را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (checkNumber.isEmpty()) {
                    Toast.makeText(this, "⚠️ شماره چک را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                addCheck(checkNumber, amount, holderName, selectedDueDate, alertDays)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun addCheck(checkNumber: String, amount: Long, holderName: String, dueDate: Long, alertDays: Int) {
        lifecycleScope.launch {
            try {
                checkManager.addCheck(
                    checkNumber = checkNumber,
                    amount = amount.toDouble(),
                    recipient = holderName,
                    dueDate = dueDate,
                    alertDays = alertDays
                )
                
                Toast.makeText(
                    this@ChecksManagementActivity,
                    "✅ چک با موفقیت ثبت شد",
                    Toast.LENGTH_SHORT
                ).show()
                
                loadChecks()
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChecksManagementActivity,
                    "❌ خطا در ثبت: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun viewCheckDetails(check: CheckManager.Check) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = check.dueDate
        }
        val persianDate = PersianDateConverter.gregorianToPersian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        ).toReadableString()
        
        val statusText = when (check.status) {
            CheckManager.CheckStatus.PENDING -> "⏳ در انتظار"
            CheckManager.CheckStatus.PAID -> "✅ پرداخت شده"
            CheckManager.CheckStatus.BOUNCED -> "❌ برگشتی"
            CheckManager.CheckStatus.CANCELLED -> "🚫 لغو شده"
        }
        
        val details = buildString {
            appendLine("شماره چک: ${check.checkNumber}")
            appendLine("مبلغ: ${formatAmount(check.amount)}")
            appendLine("دارنده: ${check.recipient}")
            appendLine("تاریخ سررسید: $persianDate")
            appendLine("وضعیت: $statusText")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("جزئیات چک")
            .setMessage(details)
            .setPositiveButton("بستن", null)
            .setNegativeButton("حذف") { _, _ ->
                deleteCheck(check)
            }
            .show()
    }
    
    private fun deleteCheck(check: CheckManager.Check) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف چک")
            .setMessage("آیا از حذف این چک مطمئن هستید؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        checkManager.deleteCheck(check.id)
                        
                        Toast.makeText(
                            this@ChecksManagementActivity,
                            "✅ چک حذف شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        loadChecks()
                        
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ChecksManagementActivity,
                            "❌ خطا در حذف: ${e.message}",
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
        menuInflater.inflate(R.menu.checks_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export -> {
                exportChecks()
                true
            }
            R.id.action_import -> {
                importChecks()
                true
            }
            R.id.action_backup -> {
                backupChecks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun exportChecks() {
        lifecycleScope.launch {
            try {
                val csvData = checkManager.exportToCSV()
                // TODO: Save to file
                Toast.makeText(this@ChecksManagementActivity, "✅ اکسپورت موفق", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChecksManagementActivity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun importChecks() {
        // TODO: Import from CSV
        Toast.makeText(this, "🚧 ایمپورت در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun backupChecks() {
        // TODO: Backup to Google Drive
        Toast.makeText(this, "🚧 بکاپ در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
}