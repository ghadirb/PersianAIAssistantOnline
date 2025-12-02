package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
enum class FilterType {
    ALL, TIME_BASED, LOCATION_BASED, RECURRING, CONDITIONAL, HIGH_PRIORITY
}

class AdvancedRemindersActivity : AppCompatActivity() {

    private lateinit var remindersAdapter: RemindersAdapter

    private lateinit var binding: ActivityAdvancedRemindersBinding
    private lateinit var smartReminderManager: SmartReminderManager
    private lateinit var advancedAssistant: AdvancedPersianAssistant
    private val allReminders = mutableListOf<SmartReminderManager.SmartReminder>()
    private var lastReminderNotification = 0L
    
    private var filterType: FilterType = FilterType.ALL

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
        supportActionBar?.title = "🔔 یادآوری‌های هوشمند"
    }
    
    private fun initializeManager() {
        smartReminderManager = SmartReminderManager(this)
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
        binding.fabAddReminder.setOnClickListener {
            showAddReminderDialog()
        }

        binding.chatFab.setOnClickListener {
            startActivity(Intent(this, ReminderChatActivity::class.java))
        }
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
                val reminders = smartReminderManager.getAllReminders()
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
                val stats = smartReminderManager.getReminderStats()
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
        val alertTypeGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.alertTypeChipGroup)
        
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
                .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
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
                val alertType = when (alertTypeGroup.checkedChipId) {
                    R.id.chipAlertFullScreen -> SmartReminderManager.AlertType.FULL_SCREEN
                    else -> SmartReminderManager.AlertType.NOTIFICATION
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
                
                smartReminderManager.createSimpleReminder(
                    title = "$category - $title",
                    description = description,
                    triggerTime = calendar.timeInMillis,
                    priority = priority,
                    alertType = alertType
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
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = EditText(this).apply {
            hint = "عنوان یادآوری مکانی"
        }
        val placeInput = EditText(this).apply {
            hint = "نام مکان (مثلاً خانه، محل کار)"
        }
        val latInput = EditText(this).apply {
            hint = "عرض جغرافیایی (lat)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val lngInput = EditText(this).apply {
            hint = "طول جغرافیایی (lng)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        container.addView(titleInput)
        container.addView(placeInput)
        container.addView(latInput)
        container.addView(lngInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("📍 یادآوری مکانی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val placeName = placeInput.text.toString().trim()
                val latText = latInput.text.toString().trim()
                val lngText = lngInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (latText.isEmpty() || lngText.isEmpty()) {
                    Toast.makeText(this, "⚠️ مختصات را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val lat = latText.toDoubleOrNull()
                val lng = lngText.toDoubleOrNull()

                if (lat == null || lng == null) {
                    Toast.makeText(this, "⚠️ مختصات نامعتبر است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val description = if (placeName.isNotEmpty()) "مکان: $placeName" else ""

                smartReminderManager.createLocationReminder(
                    title = title,
                    description = description,
                    lat = lat,
                    lng = lng,
                    locationName = placeName
                )

                Toast.makeText(this, "✅ یادآوری مکانی ذخیره شد", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showRecurringReminderDialog() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = EditText(this).apply {
            hint = "عنوان یادآوری تکراری"
        }
        val descriptionInput = EditText(this).apply {
            hint = "توضیحات (اختیاری)"
        }

        val patterns = arrayOf("روزانه", "هفتگی", "ماهانه", "سالانه", "انتخاب روزهای خاص")
        val patternSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@AdvancedRemindersActivity,
                android.R.layout.simple_spinner_item,
                patterns
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        
        val patternInfo = android.widget.TextView(this).apply {
            text = "📌 روزانه: هر روز در ساعت مشخص شده\nهفتگی: هر هفته در همان روز و ساعت\nماهانه: هر ماه در همان روز\nسالانه: هر سال در همان تاریخ\nروزهای خاص: فقط در روزهای انتخاب شده"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 8, 0, 8)
        }

        var selectedHour = 9
        var selectedMinute = 0
        val selectedDays = mutableSetOf<Int>() // 0=شنبه، 1=یکشنبه، ... 6=جمعه

        val timeButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "انتخاب ساعت"
            setOnClickListener {
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(selectedHour)
                    .setMinute(selectedMinute)
                    .setTitleText("ساعت اولین یادآوری")
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    selectedHour = timePicker.hour
                    selectedMinute = timePicker.minute
                    text = String.format("%02d:%02d", selectedHour, selectedMinute)
                }

                timePicker.show(supportFragmentManager, "RECURRING_TIME_PICKER")
            }
        }

        val daysButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "انتخاب روزهای هفته"
            isEnabled = false
            setOnClickListener {
                val dayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
                val checkedDays = BooleanArray(7) { selectedDays.contains(it) }

                MaterialAlertDialogBuilder(this@AdvancedRemindersActivity)
                    .setTitle("انتخاب روزهای هفته")
                    .setMultiChoiceItems(dayNames, checkedDays) { _, which, isChecked ->
                        if (isChecked) {
                            selectedDays.add(which)
                        } else {
                            selectedDays.remove(which)
                        }
                    }
                    .setPositiveButton("تأیید") { _, _ ->
                        val selectedDayNames = selectedDays.sorted().map { dayNames[it] }.joinToString("، ")
                        text = "روزهای انتخاب شده: $selectedDayNames"
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        }

        container.addView(titleInput)
        container.addView(descriptionInput)
        container.addView(patternSpinner)
        container.addView(patternInfo)
        container.addView(timeButton)
        container.addView(daysButton)

        patternSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                daysButton.isEnabled = position == 4 // فقط برای "انتخاب روزهای خاص"
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🔁 یادآوری تکراری")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis < System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                val pattern = when (patternSpinner.selectedItemPosition) {
                    0 -> SmartReminderManager.RepeatPattern.DAILY
                    1 -> SmartReminderManager.RepeatPattern.WEEKLY
                    2 -> SmartReminderManager.RepeatPattern.MONTHLY
                    3 -> SmartReminderManager.RepeatPattern.YEARLY
                    4 -> {
                        if (selectedDays.isEmpty()) {
                            Toast.makeText(this, "⚠️ حداقل یک روز را انتخاب کنید", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        SmartReminderManager.RepeatPattern.CUSTOM
                    }
                    else -> SmartReminderManager.RepeatPattern.DAILY
                }

                val tags = mutableListOf<String>()
                if (pattern == SmartReminderManager.RepeatPattern.CUSTOM) {
                    tags.add("days:${selectedDays.sorted().joinToString(",")}")
                }
                
                // بررسی chip انتخاب شده برای نوع هشدار (پیش‌فرض: نوتیفیکیشن)
                val useFullScreen = false
                val alertType = if (useFullScreen) {
                    SmartReminderManager.AlertType.FULL_SCREEN
                } else {
                    SmartReminderManager.AlertType.NOTIFICATION
                }
                
                if (useFullScreen) {
                    tags.add("use_alarm:true")
                }
                
                Log.d("RecurringReminder", "Alert type selected: $alertType")

                val reminder = SmartReminderManager.SmartReminder(
                    id = "recurring_${System.currentTimeMillis()}",
                    title = title,
                    description = description,
                    type = SmartReminderManager.ReminderType.RECURRING,
                    priority = SmartReminderManager.Priority.MEDIUM,
                    alertType = alertType,
                    triggerTime = calendar.timeInMillis,
                    repeatPattern = pattern,
                    customRepeatDays = if (pattern == SmartReminderManager.RepeatPattern.CUSTOM) selectedDays.toList() else emptyList(),
                    tags = tags
                )

                smartReminderManager.addReminder(reminder)

                Toast.makeText(this, "✅ یادآوری تکراری ذخیره شد", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showConditionalReminderDialog() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = EditText(this).apply {
            hint = "عنوان یادآوری شرطی"
        }
        val conditionInput = EditText(this).apply {
            hint = "شرط را بنویسید (مثال: اگر موجودی زیر ۱۰۰ هزار شد...)"
        }

        container.addView(titleInput)
        container.addView(conditionInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("⚙️ یادآوری شرطی")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val condition = conditionInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (condition.isEmpty()) {
                    Toast.makeText(this, "⚠️ شرط را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val reminder = SmartReminderManager.SmartReminder(
                    id = "conditional_${System.currentTimeMillis()}",
                    title = title,
                    description = condition,
                    type = SmartReminderManager.ReminderType.SIMPLE,
                    priority = SmartReminderManager.Priority.MEDIUM,
                    alertType = SmartReminderManager.AlertType.NOTIFICATION,
                    triggerTime = 0L,
                    tags = listOf("شرط: $condition")
                )

                smartReminderManager.addReminderWithoutAlarm(reminder)

                Toast.makeText(this, "✅ یادآوری شرطی ذخیره شد", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
        fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView = binding.remindersRecyclerView

    private fun handleReminderAction(reminder: SmartReminderManager.SmartReminder, action: String) {
        when (action) {
            "view" -> showReminderDetails(reminder)
            "edit" -> showEditReminderDialog(reminder)
            "complete" -> lifecycleScope.launch {
                if (smartReminderManager.completeReminder(reminder.id)) {
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
            .setNeutralButton("ویرایش") { _, _ -> showEditReminderDialog(reminder) }
            .setNegativeButton("حذف") { _, _ -> confirmDeleteReminder(reminder) }
            .show()
    }

    private fun showEditReminderDialog(reminder: SmartReminderManager.SmartReminder) {
        // بر اساس نوع یادآوری، dialog مناسب را نمایش بده
        if (reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
            showEditRecurringReminderDialog(reminder)
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_reminder, null)

        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.titleInput)
        val descriptionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.descriptionInput)
        val dateButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectDateButton)
        val timeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectTimeButton)
        val priorityGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.priorityChipGroup)
        val categorySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.categorySpinner)
        val alertTypeGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.alertTypeChipGroup)

        // Pre-fill data
        val (category, title) = reminder.title.split(" - ").let { if (it.size > 1) it[0] to it[1] else "" to reminder.title }
        titleInput.setText(title)
        descriptionInput.setText(reminder.description)

        var selectedDate = reminder.triggerTime
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        var selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
        var selectedMinute = calendar.get(Calendar.MINUTE)

        val persianDate = PersianDateConverter.gregorianToPersian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dateButton.text = persianDate.toReadableString()
        timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)

        when (reminder.priority) {
            SmartReminderManager.Priority.LOW -> priorityGroup.check(R.id.chipLowPriority)
            SmartReminderManager.Priority.MEDIUM -> priorityGroup.check(R.id.chipMediumPriority)
            SmartReminderManager.Priority.HIGH -> priorityGroup.check(R.id.chipHighPriority)
            else -> priorityGroup.check(R.id.chipMediumPriority)
        }

        when (reminder.alertType) {
            SmartReminderManager.AlertType.FULL_SCREEN -> alertTypeGroup.check(R.id.chipAlertFullScreen)
            else -> alertTypeGroup.check(R.id.chipAlertNotification)
        }

        val categories = arrayOf("شخصی", "کاری", "خانوادگی", "مالی", "سلامت", "خرید", "سایر")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        val categoryPosition = categories.indexOf(category)
        if (categoryPosition != -1) {
            categorySpinner.setSelection(categoryPosition)
        }

        // Listeners for date and time pickers
        dateButton.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().setTitleText("تاریخ").setSelection(selectedDate).build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = selection
                val cal = Calendar.getInstance().apply { timeInMillis = selection }
                val pDate = PersianDateConverter.gregorianToPersian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                dateButton.text = pDate.toReadableString()
            }
            datePicker.show(supportFragmentManager, "EDIT_DATE_PICKER")
        }

        timeButton.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(selectedHour).setMinute(selectedMinute).setTitleText("ساعت").build()
            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            }
            timePicker.show(supportFragmentManager, "EDIT_TIME_PICKER")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("✏️ ویرایش یادآوری")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                val newTitle = titleInput.text.toString()
                val newDescription = descriptionInput.text.toString()
                val newCategory = categories[categorySpinner.selectedItemPosition]

                val newPriority = when (priorityGroup.checkedChipId) {
                    R.id.chipLowPriority -> SmartReminderManager.Priority.LOW
                    R.id.chipMediumPriority -> SmartReminderManager.Priority.MEDIUM
                    R.id.chipHighPriority -> SmartReminderManager.Priority.HIGH
                    else -> reminder.priority
                }
                val newAlertType = when (alertTypeGroup.checkedChipId) {
                    R.id.chipAlertFullScreen -> SmartReminderManager.AlertType.FULL_SCREEN
                    else -> SmartReminderManager.AlertType.NOTIFICATION
                }

                if (newTitle.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val finalCalendar = Calendar.getInstance().apply {
                    timeInMillis = selectedDate
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }

                val updatedReminder = reminder.copy(
                    title = "$newCategory - $newTitle",
                    description = newDescription,
                    triggerTime = finalCalendar.timeInMillis,
                    priority = newPriority,
                    alertType = newAlertType
                )

                if (smartReminderManager.updateReminder(updatedReminder)) {
                    Toast.makeText(this, "✅ یادآوری به‌روز شد", Toast.LENGTH_SHORT).show()
                    loadReminders()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showEditRecurringReminderDialog(reminder: SmartReminderManager.SmartReminder) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = EditText(this).apply {
            hint = "عنوان یادآوری تکراری"
            setText(reminder.title)
        }
        val descriptionInput = EditText(this).apply {
            hint = "توضیحات (اختیاری)"
            setText(reminder.description)
        }

        val patterns = arrayOf("روزانه", "هفتگی", "ماهانه", "سالانه", "انتخاب روزهای خاص")
        val patternSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@AdvancedRemindersActivity,
                android.R.layout.simple_spinner_item,
                patterns
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        // تنظیم pattern بر اساس reminder موجود
        val currentPatternIndex = when (reminder.repeatPattern) {
            SmartReminderManager.RepeatPattern.DAILY -> 0
            SmartReminderManager.RepeatPattern.WEEKLY -> 1
            SmartReminderManager.RepeatPattern.MONTHLY -> 2
            SmartReminderManager.RepeatPattern.YEARLY -> 3
            SmartReminderManager.RepeatPattern.CUSTOM -> 4
            else -> 0
        }
        patternSpinner.setSelection(currentPatternIndex)

        var selectedHour = Calendar.getInstance().apply { timeInMillis = reminder.triggerTime }.get(Calendar.HOUR_OF_DAY)
        var selectedMinute = Calendar.getInstance().apply { timeInMillis = reminder.triggerTime }.get(Calendar.MINUTE)
        
        val selectedDays = mutableSetOf<Int>()
        // استخراج روزهای انتخاب شده از tags
        reminder.tags.forEach { tag ->
            if (tag.startsWith("days:")) {
                val days = tag.substring(5).split(",").mapNotNull { it.toIntOrNull() }
                selectedDays.addAll(days)
            }
        }

        val timeButton = com.google.android.material.button.MaterialButton(this).apply {
            text = String.format("%02d:%02d", selectedHour, selectedMinute)
            setOnClickListener {
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(selectedHour)
                    .setMinute(selectedMinute)
                    .setTitleText("ساعت یادآوری")
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    selectedHour = timePicker.hour
                    selectedMinute = timePicker.minute
                    text = String.format("%02d:%02d", selectedHour, selectedMinute)
                }

                timePicker.show(supportFragmentManager, "EDIT_RECURRING_TIME_PICKER")
            }
        }

        val daysButton = com.google.android.material.button.MaterialButton(this).apply {
            isEnabled = currentPatternIndex == 4
            // اگر روزهای خاص انتخاب شده باشند، نمایش بده
            if (selectedDays.isNotEmpty()) {
                val dayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
                val selectedDayNames = selectedDays.sorted().map { dayNames[it] }.joinToString("، ")
                text = "روزهای انتخاب شده: $selectedDayNames"
            } else {
                text = "انتخاب روزهای هفته"
            }
            setOnClickListener {
                val dayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
                val checkedDays = BooleanArray(7) { selectedDays.contains(it) }

                MaterialAlertDialogBuilder(this@AdvancedRemindersActivity)
                    .setTitle("انتخاب روزهای هفته")
                    .setMultiChoiceItems(dayNames, checkedDays) { _, which, isChecked ->
                        if (isChecked) {
                            selectedDays.add(which)
                        } else {
                            selectedDays.remove(which)
                        }
                    }
                    .setPositiveButton("تأیید") { _, _ ->
                        val selectedDayNames = selectedDays.sorted().map { dayNames[it] }.joinToString("، ")
                        text = "روزهای انتخاب شده: $selectedDayNames"
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        }

        val alertTypeGroup = com.google.android.material.chip.ChipGroup(this).apply {
            isSingleSelection = false
            val chipNotification = com.google.android.material.chip.Chip(this@AdvancedRemindersActivity).apply {
                id = 1
                text = "📱 نوتیفیکیشن"
                isCheckable = true
                isChecked = true
            }
            val chipFullScreen = com.google.android.material.chip.Chip(this@AdvancedRemindersActivity).apply {
                id = 2
                text = "🔔 تمام‌صفحه"
                isCheckable = reminder.tags.any { it.startsWith("use_alarm:true") }
            }
            addView(chipNotification)
            addView(chipFullScreen)
        }
        
        container.addView(titleInput)
        container.addView(descriptionInput)
        container.addView(patternSpinner)
        container.addView(timeButton)
        container.addView(daysButton)
        container.addView(alertTypeGroup)

        patternSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                daysButton.isEnabled = position == 4
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("✏️ ویرایش یادآوری تکراری")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(this, "⚠️ عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = reminder.triggerTime
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                }

                val pattern = when (patternSpinner.selectedItemPosition) {
                    0 -> SmartReminderManager.RepeatPattern.DAILY
                    1 -> SmartReminderManager.RepeatPattern.WEEKLY
                    2 -> SmartReminderManager.RepeatPattern.MONTHLY
                    3 -> SmartReminderManager.RepeatPattern.YEARLY
                    4 -> {
                        if (selectedDays.isEmpty()) {
                            Toast.makeText(this, "⚠️ حداقل یک روز را انتخاب کنید", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        SmartReminderManager.RepeatPattern.CUSTOM
                    }
                    else -> SmartReminderManager.RepeatPattern.DAILY
                }

                val tags = mutableListOf<String>()
                if (pattern == SmartReminderManager.RepeatPattern.CUSTOM) {
                    tags.add("days:${selectedDays.sorted().joinToString(",")}")
                }
                val useFullScreen = alertTypeGroup.checkedChipIds.contains(2)
                if (useFullScreen) {
                    tags.add("use_alarm:true")
                }
                
                val newAlertType = if (useFullScreen) {
                    SmartReminderManager.AlertType.FULL_SCREEN
                } else {
                    SmartReminderManager.AlertType.NOTIFICATION
                }
                
                val updatedReminder = reminder.copy(
                    title = title,
                    description = description,
                    alertType = newAlertType,
                    triggerTime = calendar.timeInMillis,
                    repeatPattern = pattern,
                    tags = tags
                )

                if (smartReminderManager.updateReminder(updatedReminder)) {
                    Toast.makeText(this, "✅ یادآوری تکراری به‌روز شد", Toast.LENGTH_SHORT).show()
                    loadReminders()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun confirmDeleteReminder(reminder: SmartReminderManager.SmartReminder) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف یادآوری")
            .setMessage("آیا از حذف ${reminder.title} مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    if (smartReminderManager.deleteReminder(reminder.id)) {
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
                    lifecycleScope.launch {
                        try {
                            val response = advancedAssistant.processRequestWithAI(
                                userText,
                                contextHint = "یادآوری‌ها و برنامه‌ریزی کارهای روزانه"
                            )

                            MaterialAlertDialogBuilder(this@AdvancedRemindersActivity)
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
                            Toast.makeText(this@AdvancedRemindersActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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
    
    // Test method to trigger full-screen alarm immediately
    private fun testFullScreenAlarm() {
        val testReminder = SmartReminderManager.SmartReminder(
            id = "test_${System.currentTimeMillis()}",
            title = "🔔 تست یادآوری تمام‌صفحه",
            description = "این یک تست است",
            type = SmartReminderManager.ReminderType.SIMPLE,
            priority = SmartReminderManager.Priority.HIGH,
            alertType = SmartReminderManager.AlertType.FULL_SCREEN,
            triggerTime = System.currentTimeMillis() + 2000, // 2 seconds from now
            repeatPattern = SmartReminderManager.RepeatPattern.ONCE,
            tags = listOf("use_alarm:true")
        )
        
        smartReminderManager.addReminder(testReminder)
        Toast.makeText(this, "✅ یادآوری تست برای ۲ ثانیه بعد ایجاد شد", Toast.LENGTH_SHORT).show()
        loadReminders()
    }
    
    private fun testFullScreenAlarmRecurring() {
        val testReminder = SmartReminderManager.SmartReminder(
            id = "test_recurring_${System.currentTimeMillis()}",
            title = "🔔 تست یادآوری تکراری تمام‌صفحه",
            description = "این یک تست یادآوری تکراری است",
            type = SmartReminderManager.ReminderType.RECURRING,
            priority = SmartReminderManager.Priority.HIGH,
            alertType = SmartReminderManager.AlertType.FULL_SCREEN,
            triggerTime = System.currentTimeMillis() + 3000,
            repeatPattern = SmartReminderManager.RepeatPattern.DAILY,
            tags = listOf("use_alarm:true")
        )
        
        smartReminderManager.addReminder(testReminder)
        Toast.makeText(this, "✅ یادآوری تکراری تست برای ۳ ثانیه بعد ایجاد شد", Toast.LENGTH_SHORT).show()
        loadReminders()
    }
}
