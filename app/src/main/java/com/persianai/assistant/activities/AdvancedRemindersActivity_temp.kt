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
 * غŒط§ط¯ط¢ظˆط±غŒâ€Œظ‡ط§غŒ ظ¾غŒط´ط±ظپطھظ‡
 * 
 * âœ… غŒط§ط¯ط¢ظˆط±غŒ ط²ظ…ط§ظ†غŒ
 * âœ… غŒط§ط¯ط¢ظˆط±غŒ ظ…ع©ط§ظ†غŒ (GPS)
 * âœ… غŒط§ط¯ط¢ظˆط±غŒ طھع©ط±ط§ط±غŒ
 * âœ… غŒط§ط¯ط¢ظˆط±غŒ ط´ط±ط·غŒ
 * âœ… ط§ظˆظ„ظˆغŒطھâ€Œط¨ظ†ط¯غŒ
 * âœ… ط¯ط³طھظ‡â€Œط¨ظ†ط¯غŒ
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
        supportActionBar?.title = "ًں”” غŒط§ط¯ط¢ظˆط±غŒâ€Œظ‡ط§غŒ ظ‡ظˆط´ظ…ظ†ط¯"
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
                Toast.makeText(this@AdvancedRemindersActivity, " ط®ط·ط§: ${e.message}", Toast.LENGTH_SHORT).show()
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
                reminder.tags.any { tag -> tag.contains("ط´ط±ط·") || tag.contains("condition", ignoreCase = true) }
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
            "âڈ° غŒط§ط¯ط¢ظˆط±غŒ ط²ظ…ط§ظ†غŒ",
            "ًں“چ غŒط§ط¯ط¢ظˆط±غŒ ظ…ع©ط§ظ†غŒ",
            "ًں”پ غŒط§ط¯ط¢ظˆط±غŒ طھع©ط±ط§ط±غŒ",
            "âڑ™ï¸ڈ غŒط§ط¯ط¢ظˆط±غŒ ط´ط±ط·غŒ"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("ظ†ظˆط¹ غŒط§ط¯ط¢ظˆط±غŒ")
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
        val categories = arrayOf("ط´ط®طµغŒ", "ع©ط§ط±غŒ", "ط®ط§ظ†ظˆط§ط¯ع¯غŒ", "ظ…ط§ظ„غŒ", "ط³ظ„ط§ظ…طھ", "ط®ط±غŒط¯", "ط³ط§غŒط±")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        
        dateButton.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("طھط§ط±غŒط®")
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
                .setTitleText("ط³ط§ط¹طھ")
                .build()
            
            timePicker.addOnPositiveButtonClickListener {
                selectedHour = timePicker.hour
                selectedMinute = timePicker.minute
                timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            }
            
            timePicker.show(supportFragmentManager, "TIME_PICKER")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("â‍• غŒط§ط¯ط¢ظˆط±غŒ ط²ظ…ط§ظ†غŒ")
            .setView(dialogView)
            .setPositiveButton("ط°ط®غŒط±ظ‡") { _, _ ->
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
                    Toast.makeText(this, "âڑ ï¸ڈ ط¹ظ†ظˆط§ظ† ط±ط§ ظˆط§ط±ط¯ ع©ظ†غŒط¯", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // ظ…ط­ط§ط³ط¨ظ‡ ط²ظ…ط§ظ† ط¯ظ‚غŒظ‚
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
                    "غŒط§ط¯ط¢ظˆط±غŒ ط¬ط¯غŒط¯ ط«ط¨طھ ط´ط¯",
                    "$title ط¨ط±ط§غŒ ${PersianDateConverter.gregorianToPersian(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH)+1, calendar.get(Calendar.DAY_OF_MONTH)).toReadableString()}"
                )
                loadReminders()
            }
            .setNegativeButton("ظ„ط؛ظˆ", null)
            .show()
    }
    
    private fun showLocationBasedReminderDialog() {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val titleInput = EditText(this).apply {
            hint = "ط¹ظ†ظˆط§ظ† غŒط§ط¯ط¢ظˆط±غŒ ظ…ع©ط§ظ†غŒ"
        }
        val placeInput = EditText(this).apply {
            hint = "ظ†ط§ظ… ظ…ع©ط§ظ† (ظ…ط«ظ„ط§ظ‹ ط®ط§ظ†ظ‡طŒ ظ…ط­ظ„ ع©ط§ط±)"
        }
        val latInput = EditText(this).apply {
            hint = "ط¹ط±ط¶ ط¬ط؛ط±ط§ظپغŒط§غŒغŒ (lat)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val lngInput = EditText(this).apply {
            hint = "ط·ظˆظ„ ط¬ط؛ط±ط§ظپغŒط§غŒغŒ (lng)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        container.addView(titleInput)
        container.addView(placeInput)
        container.addView(latInput)
        container.addView(lngInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("ًں“چ غŒط§ط¯ط¢ظˆط±غŒ ظ…ع©ط§ظ†غŒ")
            .setView(container)
            .setPositiveButton("ط°ط®غŒط±ظ‡") { _, _ ->
                val title = titleInput.text.toString().trim()
                val placeName = placeInput.text.toString().trim()
                val latText = latInput.text.toString().trim()
                val lngText = lngInput.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(this, "âڑ ï¸ڈ ط¹ظ†ظˆط§ظ† ط±ط§ ظˆط§ط±ط¯ ع©ظ†غŒط¯", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (latText.isEmpty() || lngText.isEmpty()) {
                    Toast.makeText(this, "âڑ ï¸ڈ ظ…ط®طھطµط§طھ ط±ط§ ظˆط§ط±ط¯ ع©ظ†غŒط¯", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val lat = latText.toDoubleOrNull()
                val lng = lngText.toDoubleOrNull()

                if (lat == null || lng == null) {
                    Toast.makeText(this, "âڑ ï¸ڈ ظ…ط®طھطµط§طھ ظ†ط§ظ…ط¹طھط¨ط± ط§ط³طھ", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val description = if (placeName.isNotEmpty()) "ظ…ع©ط§ظ†: $placeName" else ""

                smartReminderManager.createLocationReminder(
                    title = title,
                    description = description,
                    lat = lat,
                    lng = lng,
                    locationName = placeName
                )

                Toast.makeText(this, "âœ… غŒط§ط¯ط¢ظˆط±غŒ ظ…ع©ط§ظ†غŒ ط°ط®غŒط±ظ‡ ط´ط¯", Toast.LENGTH_SHORT).show()
                loadReminders()
            }
            .setNegativeButton("ظ„ط؛ظˆ", null)
            .show()
    }
    
