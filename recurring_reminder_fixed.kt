// متد showRecurringReminderDialog - نسخهٔ اصلاح شده
private fun showRecurringReminderDialog() {
    val dialogView = layoutInflater.inflate(R.layout.dialog_recurring_reminder, null)
    
    val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.recurringTitleInput)
    val descriptionInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.recurringDescriptionInput)
    val patternSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.recurringPatternSpinner)
    val timeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.recurringSelectTimeButton)
    val daysButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.recurringSelectDaysButton)
    val alertTypeGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.recurringAlertTypeChipGroup)
    
    // تنظیم spinner
    val patterns = arrayOf("روزانه", "هفتگی", "ماهانه", "سالانه", "انتخاب روزهای خاص")
    patternSpinner.adapter = android.widget.ArrayAdapter(
        this,
        android.R.layout.simple_spinner_item,
        patterns
    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    
    // تنظیم پیش‌فرض chip
    alertTypeGroup.check(R.id.chipRecurringAlertNotification)
    
    var selectedHour = 9
    var selectedMinute = 0
    val selectedDays = mutableSetOf<Int>()
    
    // تنظیم دکمه زمان
    timeButton.setOnClickListener {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText("ساعت اولین یادآوری")
            .build()
        
        timePicker.addOnPositiveButtonClickListener {
            selectedHour = timePicker.hour
            selectedMinute = timePicker.minute
            timeButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }
        
        timePicker.show(supportFragmentManager, "RECURRING_TIME_PICKER")
    }
    
    // تنظیم دکمه روزها
    daysButton.setOnClickListener {
        val dayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
        val checkedDays = BooleanArray(7) { selectedDays.contains(it) }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب روزهای هفته")
            .setMultiChoiceItems(dayNames, checkedDays) { _, which, isChecked ->
                if (isChecked) selectedDays.add(which) else selectedDays.remove(which)
            }
            .setPositiveButton("تأیید") { _, _ ->
                val selectedDayNames = selectedDays.sorted().map { dayNames[it] }.joinToString("، ")
                daysButton.text = "روزهای انتخاب شده: $selectedDayNames"
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    // تنظیم spinner listener
    patternSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            daysButton.isEnabled = position == 4
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
    
    // نمایش dialog
    MaterialAlertDialogBuilder(this)
        .setTitle("🔁 یادآوری تکراری")
        .setView(dialogView)
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
            
            // خواندن chip انتخاب شده
            val checkedChipId = alertTypeGroup.checkedChipId
            val useFullScreen = checkedChipId == R.id.chipRecurringAlertFullScreen
            val alertType = if (useFullScreen) {
                SmartReminderManager.AlertType.FULL_SCREEN
            } else {
                SmartReminderManager.AlertType.NOTIFICATION
            }
            
            if (useFullScreen) {
                tags.add("use_alarm:true")
            }
            
            Log.d("RecurringReminder", "Alert type: $alertType, useFullScreen: $useFullScreen")
            
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
