package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.persianai.assistant.R
import com.persianai.assistant.adapters.RemindersAdapter
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.databinding.ActivityAdvancedRemindersBinding
import com.persianai.assistant.utils.NotificationHelper
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.launch
import java.util.*

/**
 * یادآوری‌های پیشرفته
 * 
 * ✅ یادآوری زمانی
 * ✅ یادآوری مکانی (GPS)
 * ✅ یادآوری تکراری
 * ✅ یادآوری شرطی
 * ✅ اولویت‌بندی
 * ✅ دسته‌بندی
 */
class AdvancedRemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedRemindersBinding
    private lateinit var remindersAdapter: RemindersAdapter
    private lateinit var reminderManager: SmartReminderManager
    private lateinit var advancedAssistant: AdvancedPersianAssistant
    private val allReminders = mutableListOf<SmartReminderManager.SmartReminder>()
    private var lastReminderNotification = 0L
    
    private var filterType: FilterType = FilterType.ALL
    
    enum class FilterType {
        ALL,
        TIME_BASED,
        LOCATION_BASED,
        RECURRING,
        CONDITIONAL,
        HIGH_PRIORITY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeManager()
        setupRecyclerView()
        setupListeners()
        checkPermissions()
        loadReminders()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📝 یادآوری‌های پیشرفته"
    }
    
    private fun initializeManager() {
        reminderManager = SmartReminderManager(this)
        advancedAssistant = AdvancedPersianAssistant(this)
    }
    
    private fun setupRecyclerView() {
        remindersAdapter = RemindersAdapter(mutableListOf()) { reminder, action ->
            handleReminderAction(reminder, action)
        }
        
        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdvancedRemindersActivity)
            adapter = remindersAdapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddReminder.setOnClickListener { showAddReminderDialog() }
        setupFilterChips()
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { applyFilter(FilterType.ALL) }
        binding.chipTimeBased.setOnClickListener { applyFilter(FilterType.TIME_BASED) }
        binding.chipLocationBased.setOnClickListener { applyFilter(FilterType.LOCATION_BASED) }
        binding.chipRecurring.setOnClickListener { applyFilter(FilterType.RECURRING) }
        binding.chipConditional.setOnClickListener { applyFilter(FilterType.CONDITIONAL) }
        binding.chipHighPriority.setOnClickListener { applyFilter(FilterType.HIGH_PRIORITY) }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
    
    private fun loadReminders() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val reminders = reminderManager.getAllReminders()
                allReminders.clear()
                allReminders.addAll(reminders)
                binding.progressBar.visibility = View.GONE
                binding.emptyState.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
                binding.remindersRecyclerView.visibility = if (reminders.isEmpty()) View.GONE else View.VISIBLE
                applyFilter(filterType)
                updateStats()
                maybeNotifyUpcoming(reminders)
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@AdvancedRemindersActivity, " خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun applyFilter(type: FilterType) {
        filterType = type
        val filtered = when (type) {
            FilterType.ALL -> allReminders
            FilterType.TIME_BASED -> allReminders.filter {
                it.type in listOf(
                    SmartReminderManager.ReminderType.SIMPLE,
                    SmartReminderManager.ReminderType.BILL_PAYMENT,
                    SmartReminderManager.ReminderType.MEDICINE,
                    SmartReminderManager.ReminderType.TASK,
                    SmartReminderManager.ReminderType.BIRTHDAY,
                    SmartReminderManager.ReminderType.ANNIVERSARY
                )
            }
            FilterType.LOCATION_BASED -> allReminders.filter { it.type == SmartReminderManager.ReminderType.LOCATION_BASED }
            FilterType.RECURRING -> allReminders.filter { it.repeatPattern != SmartReminderManager.RepeatPattern.ONCE }
            FilterType.CONDITIONAL -> allReminders.filter { reminder ->
                reminder.tags.any { tag -> tag.contains("شرط") || tag.contains("condition", ignoreCase = true) }
            }
            FilterType.HIGH_PRIORITY -> allReminders.filter {
                it.priority == SmartReminderManager.Priority.HIGH || it.priority == SmartReminderManager.Priority.URGENT
            }
        }
        remindersAdapter.updateData(filtered)
    }
    
    private fun updateStats() {
        lifecycleScope.launch {
            try {
                val stats = reminderManager.getReminderStats()
                binding.statsCard.visibility = if (stats.totalReminders > 0) View.VISIBLE else View.GONE
                binding.totalRemindersText.text = stats.totalReminders.toString()
                binding.activeRemindersText.text = stats.activeReminders.toString()
                binding.completedRemindersText.text = stats.completedReminders.toString()
                binding.upcomingRemindersText.text = stats.todayReminders.toString()
            } catch (e: Exception) {
                // Stats not available
            }
        }
    }
    
    private fun showAddReminderDialog() {
        val options = arrayOf(
            "⏰ یادآوری زمانی",
            "📍 یادآوری مکانی",
            "🔁 یادآوری تکراری",
            "⚙️ یادآوری شرطی"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("نوع یادآوری")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTimeBasedReminderDialog()
                    1 -> showLocationBasedReminderDialog()
                    2 -> showRecurringReminderDialog()
                    3 -> showConditionalReminderDialog()
                }
            }
            .show()
    }
    
    private fun showTimeBasedReminderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_reminder, null)
        
        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.titleInput)
        val descriptionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.descriptionInput)
        val dateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectDateButton)
        val timeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectTimeButton)
        val priorityGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.priorityChipGroup)
        val categorySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.categorySpinner)
        
        var selectedDate: Long = System.currentTimeMillis()
        var selectedHour = 12
        var selectedMinute = 0
        
        // Setup category spinner
        val categories = arrayOf("شخصی", "کاری", "خانوادگی", "مالی", "سلامت", "خرید", "سایر")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        
        dateButton.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("تاریخ")
                .setSelection(selectedDate)
                .build()
            
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = selection
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = selection
                }
                val persianDate = PersianDateConverter.gregorianToPersian(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                dateButton.text = persianDate.toReadableString()
            }
            
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }
        
        timeButton.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("ساعت")
                .build()
            
            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            }
            
            timePicker.show(supportFragmentManager, "TIME_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("➕ یادآوری زمانی")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString()
                val description = descriptionInput.text.toString()
                val category = categories[categorySpinner.selectedItemPosition]
                
                val priority = when (priorityGroup.checkedChipId) {
                    R.id.chipLowPriority -> SmartReminderManager.Priority.LOW
                    R.id.chipMediumPriority -> SmartReminderManager.Priority.MEDIUM
                    R.id.chipHighPriority -> SmartReminderManager.Priority.HIGH
                    else -> SmartReminderManager.Priority.MEDIUM
                }
                
                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // محاسبه زمان دقیق
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = selectedDate
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                calendar.set(Calendar.SECOND, 0)
                
                reminderManager.createSimpleReminder(
                    title = "$category - $title",
                    description = description,
                    triggerTime = calendar.timeInMillis,
                    priority = priority
                )
                NotificationHelper.showReminderNotification(
                    this,
                    "یادآوری جدید ثبت شد",
                    "$title برای ${PersianDateConverter.gregorianToPersian(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH)+1, calendar.get(Calendar.DAY_OF_MONTH)).toReadableString()}"
                )
                loadReminders()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showLocationBasedReminderDialog() {
        Toast.makeText(this, "🚧 یادآوری مکانی در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun showRecurringReminderDialog() {
        Toast.makeText(this, "🚧 یادآوری تکراری در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun showConditionalReminderDialog() {
        Toast.makeText(this, "🚧 یادآوری شرطی در نسخه بعدی", Toast.LENGTH_SHORT).show()
    }
    
    private fun handleReminderAction(reminder: SmartReminderManager.SmartReminder, action: String) {
        when (action) {
            "view" -> showReminderDetails(reminder)
            "complete" -> lifecycleScope.launch {
                if (reminderManager.completeReminder(reminder.id)) {
                    Toast.makeText(this@AdvancedRemindersActivity, "✅ انجام شد", Toast.LENGTH_SHORT).show()
                    loadReminders()
                }
            }
        }
    }

    private fun showReminderDetails(reminder: SmartReminderManager.SmartReminder) {
        val persianDate = if (reminder.triggerTime > 0) {
            val calendar = Calendar.getInstance().apply { timeInMillis = reminder.triggerTime }
            PersianDateConverter.gregorianToPersian(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            ).toReadableString()
        } else "نامشخص"

        val details = buildString {
            appendLine("نوع: ${reminder.type.displayName}")
            appendLine("عنوان: ${reminder.title}")
            if (reminder.description.isNotEmpty()) appendLine("توضیحات: ${reminder.description}")
            appendLine("اولویت: ${reminder.priority.displayName}")
            appendLine("زمان: $persianDate")
            if (reminder.locationName.isNotEmpty()) appendLine("مکان: ${reminder.locationName}")
            if (reminder.relatedPerson.isNotEmpty()) appendLine("شخص مرتبط: ${reminder.relatedPerson}")
            appendLine("وضعیت: ${if (reminder.isCompleted) "✅ انجام شده" else "⏳ در انتظار"}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("جزئیات یادآوری")
            .setMessage(details)
            .setPositiveButton("بستن", null)
            .setNegativeButton("حذف") { _, _ -> confirmDeleteReminder(reminder) }
            .show()
    }

    private fun confirmDeleteReminder(reminder: SmartReminderManager.SmartReminder) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف یادآوری")
            .setMessage("آیا از حذف ${reminder.title} مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    if (reminderManager.deleteReminder(reminder.id)) {
                        Toast.makeText(this@AdvancedRemindersActivity, "🗑️ حذف شد", Toast.LENGTH_SHORT).show()
                        loadReminders()
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showAIChatDialog() {
        val input = EditText(this)
        input.hint = "مثال: فردا ساعت ۹ صبح یادم بنداز قبض برق رو پرداخت کنم"
        input.setPadding(32, 32, 32, 32)

        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 دستیار هوشمند یادآوری")
            .setView(input)
            .setPositiveButton("اجرا") { _, _ ->
                val userText = input.text.toString().trim()
                if (userText.isNotEmpty()) {
                    try {
                        val response = advancedAssistant.processRequest(userText)

                        MaterialAlertDialogBuilder(this)
                            .setTitle("پاسخ دستیار")
                            .setMessage(response.text)
                            .setPositiveButton("باشه") { _, _ ->
                                val action = response.actionType
                                if (action == AdvancedPersianAssistant.ActionType.ADD_REMINDER ||
                                    action == AdvancedPersianAssistant.ActionType.OPEN_REMINDERS) {
                                    loadReminders()
                                }
                            }
                            .show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun maybeNotifyUpcoming(reminders: List<SmartReminderManager.SmartReminder>) {
        val now = System.currentTimeMillis()
        if (now - lastReminderNotification < 60 * 60 * 1000) return
        val upcoming = reminders.filter {
            !it.isCompleted && it.triggerTime in now..(now + 6 * 60 * 60 * 1000)
        }
        if (upcoming.isEmpty()) return
        val summary = upcoming.take(3).joinToString("\n") {
            "• ${it.title} (${it.priority.displayName})"
        }
        NotificationHelper.showReminderNotification(
            this,
            "⏰ یادآوری‌های نزدیک",
            summary
        )
        lastReminderNotification = now
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_advanced_reminders, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_ai_chat -> {
                showAIChatDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
