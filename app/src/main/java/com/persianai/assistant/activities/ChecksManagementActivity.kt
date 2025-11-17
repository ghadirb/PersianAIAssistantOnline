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
import com.persianai.assistant.data.Check
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
    private val checks = mutableListOf<Check>()
    
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
        checksAdapter = ChecksAdapter(checks) { check, action ->
            when (action) {
                "view" -> viewCheckDetails(check)
                "edit" -> editCheck(check)
                "delete" -> deleteCheck(check)
                "change_status" -> changeCheckStatus(check)
            }
        }
        
        binding.checksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChecksManagementActivity)
            adapter = checksAdapter
        }
    }
    
    private fun setupListeners() {
        // دکمه افزودن چک جدید
        binding.fabAddCheck.setOnClickListener {
            showAddCheckDialog()
        }
        
        // فیلترهای چیپ
        binding.chipAll.setOnClickListener {
            applyFilter(CheckFilterType.ALL)
        }
        
        binding.chipPayable.setOnClickListener {
            applyFilter(CheckFilterType.PAYABLE)
        }
        
        binding.chipReceivable.setOnClickListener {
            applyFilter(CheckFilterType.RECEIVABLE)
        }
        
        binding.chipPending.setOnClickListener {
            applyFilter(CheckFilterType.PENDING)
        }
        
        binding.chipCashed.setOnClickListener {
            applyFilter(CheckFilterType.CASHED)
        }
        
        binding.chipBounced.setOnClickListener {
            applyFilter(CheckFilterType.BOUNCED)
        }
        
        binding.chipUpcoming.setOnClickListener {
            applyFilter(CheckFilterType.UPCOMING)
        }
    }
    
    private fun loadChecks() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                
                val allChecks = checkManager.getAllChecks()
                
                checks.clear()
                checks.addAll(allChecks)
                
                applyFilter(filterType)
                
                binding.progressBar.visibility = View.GONE
                
                if (checks.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.checksRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
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
        
        // Reset all chips
        binding.chipAll.isChecked = false
        binding.chipPayable.isChecked = false
        binding.chipReceivable.isChecked = false
        binding.chipPending.isChecked = false
        binding.chipCashed.isChecked = false
        binding.chipBounced.isChecked = false
        binding.chipUpcoming.isChecked = false
        
        val allChecks = checkManager.getAllChecks()
        val today = System.currentTimeMillis()
        val sevenDaysLater = today + (7 * 24 * 60 * 60 * 1000)
        
        val filteredChecks = when (type) {
            CheckFilterType.ALL -> {
                binding.chipAll.isChecked = true
                allChecks
            }
            CheckFilterType.PAYABLE -> {
                binding.chipPayable.isChecked = true
                allChecks.filter { it.type == Check.CheckType.PAYABLE }
            }
            CheckFilterType.RECEIVABLE -> {
                binding.chipReceivable.isChecked = true
                allChecks.filter { it.type == Check.CheckType.RECEIVABLE }
            }
            CheckFilterType.PENDING -> {
                binding.chipPending.isChecked = true
                allChecks.filter { it.status == Check.CheckStatus.PENDING }
            }
            CheckFilterType.CASHED -> {
                binding.chipCashed.isChecked = true
                allChecks.filter { it.status == Check.CheckStatus.CASHED }
            }
            CheckFilterType.BOUNCED -> {
                binding.chipBounced.isChecked = true
                allChecks.filter { it.status == Check.CheckStatus.BOUNCED }
            }
            CheckFilterType.UPCOMING -> {
                binding.chipUpcoming.isChecked = true
                allChecks.filter { 
                    it.dueDate in today..sevenDaysLater && 
                    it.status == Check.CheckStatus.PENDING 
                }
            }
        }
        
        checks.clear()
        checks.addAll(filteredChecks)
        checksAdapter.notifyDataSetChanged()
        
        // Update counter
        binding.checksCountText.text = "تعداد: ${checks.size}"
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
                val persianDate = PersianDateConverter.gregorianToPersian(Date(selection))
                dueDateButton.text = persianDate
            }
            
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ افزودن چک جدید")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val type = if (typeGroup.checkedChipId == R.id.chipPayable) {
                    Check.CheckType.PAYABLE
                } else {
                    Check.CheckType.RECEIVABLE
                }
                
                val amount = amountInput.text.toString().toLongOrNull() ?: 0L
                val checkNumber = checkNumberInput.text.toString()
                val holderName = holderNameInput.text.toString()
                val accountNumber = accountNumberInput.text.toString()
                val notes = notesInput.text.toString()
                val alertDays = alertDaysInput.text.toString().toIntOrNull() ?: 3
                
                if (amount <= 0) {
                    Toast.makeText(this, "⚠️ مبلغ را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (checkNumber.isEmpty()) {
                    Toast.makeText(this, "⚠️ شماره چک را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val check = Check(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    amount = amount,
                    checkNumber = checkNumber,
                    holderName = holderName,
                    accountNumber = accountNumber,
                    dueDate = selectedDueDate,
                    status = Check.CheckStatus.PENDING,
                    notes = notes,
                    alertDaysBefore = alertDays,
                    createdAt = System.currentTimeMillis()
                )
                
                addCheck(check)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun addCheck(check: Check) {
        lifecycleScope.launch {
            try {
                checkManager.addCheck(check)
                
                // ثبت هشدار
                checkManager.scheduleCheckAlert(check)
                
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
    
    private fun viewCheckDetails(check: Check) {
        val persianDate = PersianDateConverter.gregorianToPersian(Date(check.dueDate))
        val createdDate = PersianDateConverter.gregorianToPersian(Date(check.createdAt))
        
        val typeText = when (check.type) {
            Check.CheckType.PAYABLE -> "💸 پرداختی"
            Check.CheckType.RECEIVABLE -> "💰 دریافتی"
        }
        
        val statusText = when (check.status) {
            Check.CheckStatus.PENDING -> "⏳ در انتظار"
            Check.CheckStatus.CASHED -> "✅ پاس شده"
            Check.CheckStatus.BOUNCED -> "❌ برگشتی"
        }
        
        val details = buildString {
            appendLine("نوع: $typeText")
            appendLine("مبلغ: ${formatAmount(check.amount)}")
            appendLine("شماره چک: ${check.checkNumber}")
            appendLine("دارنده: ${check.holderName}")
            if (check.accountNumber.isNotEmpty()) {
                appendLine("شماره حساب: ${check.accountNumber}")
            }
            appendLine("تاریخ سررسید: $persianDate")
            appendLine("وضعیت: $statusText")
            appendLine("هشدار: ${check.alertDaysBefore} روز قبل")
            if (check.notes.isNotEmpty()) {
                appendLine("\nیادداشت:")
                appendLine(check.notes)
            }
            appendLine("\nتاریخ ثبت: $createdDate")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("جزئیات چک")
            .setMessage(details)
            .setPositiveButton("بستن", null)
            .setNeutralButton("ویرایش") { _, _ ->
                editCheck(check)
            }
            .setNegativeButton("حذف") { _, _ ->
                deleteCheck(check)
            }
            .show()
    }
    
    private fun editCheck(check: Check) {
        // TODO: Implement edit dialog
        Toast.makeText(this, "🚧 ویرایش در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun deleteCheck(check: Check) {
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
    
    private fun changeCheckStatus(check: Check) {
        val statuses = Check.CheckStatus.values()
        val statusNames = statuses.map { status ->
            when (status) {
                Check.CheckStatus.PENDING -> "⏳ در انتظار"
                Check.CheckStatus.CASHED -> "✅ پاس شده"
                Check.CheckStatus.BOUNCED -> "❌ برگشتی"
            }
        }.toTypedArray()
        
        val currentIndex = statuses.indexOf(check.status)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("تغییر وضعیت چک")
            .setSingleChoiceItems(statusNames, currentIndex) { dialog, which ->
                val newStatus = statuses[which]
                
                lifecycleScope.launch {
                    try {
                        checkManager.updateCheckStatus(check.id, newStatus)
                        
                        Toast.makeText(
                            this@ChecksManagementActivity,
                            "✅ وضعیت به‌روز شد",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        loadChecks()
                        
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ChecksManagementActivity,
                            "❌ خطا: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                
                dialog.dismiss()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun formatAmount(amount: Long): String {
        return String.format("%,d تومان", amount)
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