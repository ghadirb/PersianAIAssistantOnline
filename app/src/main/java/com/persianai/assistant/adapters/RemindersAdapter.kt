package com.persianai.assistant.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.databinding.ItemReminderBinding
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.SmartReminderManager
import java.util.Calendar

class RemindersAdapter(
    private val reminders: MutableList<SmartReminderManager.SmartReminder>,
    private val onAction: (SmartReminderManager.SmartReminder, String) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    inner class ReminderViewHolder(val binding: ItemReminderBinding) : 
        RecyclerView.ViewHolder(binding.root)

    fun updateData(newReminders: List<SmartReminderManager.SmartReminder>) {
        reminders.clear()
        reminders.addAll(newReminders)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        
        with(holder.binding) {
            titleText.text = reminder.title
            
            // Type icon
            val typeIcon = when (reminder.type) {
                SmartReminderManager.ReminderType.SIMPLE -> "⏰"
                SmartReminderManager.ReminderType.RECURRING -> "🔁"
                SmartReminderManager.ReminderType.LOCATION_BASED -> "📍"
                SmartReminderManager.ReminderType.BIRTHDAY -> "🎂"
                SmartReminderManager.ReminderType.ANNIVERSARY -> "💞"
                SmartReminderManager.ReminderType.BILL_PAYMENT -> "💳"
                SmartReminderManager.ReminderType.MEDICINE -> "💊"
                SmartReminderManager.ReminderType.FAMILY -> "👨‍👩‍👧"
                SmartReminderManager.ReminderType.SHOPPING -> "🛍️"
                SmartReminderManager.ReminderType.TASK -> "📋"
            }
            typeIconText.text = typeIcon
            
            // Description
            if (reminder.description.isNotEmpty()) {
                descriptionText.text = reminder.description
                descriptionText.visibility = android.view.View.VISIBLE
            } else {
                descriptionText.visibility = android.view.View.GONE
            }
            
            // Time
            if (reminder.triggerTime > 0) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = reminder.triggerTime
                }
                val persianDate = PersianDateConverter.gregorianToPersian(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                timeText.text = persianDate.toReadableString()
                timeText.visibility = android.view.View.VISIBLE
            } else {
                timeText.visibility = android.view.View.GONE
            }
            
            // Priority color
            val priorityColor = Color.parseColor(reminder.priority.color)
            priorityIndicator.setBackgroundColor(priorityColor)
            
            // Status
            if (reminder.isCompleted) {
                statusText.text = "✅ انجام شده"
                statusText.setTextColor(Color.parseColor("#4CAF50"))
                root.alpha = 0.6f
            } else if (reminder.isOverdue()) {
                statusText.text = "⏰ سررسید گذشته"
                statusText.setTextColor(Color.parseColor("#F44336"))
                root.alpha = 1.0f
            } else if (reminder.isUpcoming()) {
                statusText.text = "⚠️ نزدیک"
                statusText.setTextColor(Color.parseColor("#FF9800"))
                root.alpha = 1.0f
            } else {
                statusText.text = "⏳ در انتظار"
                statusText.setTextColor(Color.parseColor("#2196F3"))
                root.alpha = 1.0f
            }
            
            // Category chip
            categoryChip.text = reminder.tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: reminder.type.displayName
            
            // Recurring indicator
            if (reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
                recurringIcon.visibility = android.view.View.VISIBLE
            } else {
                recurringIcon.visibility = android.view.View.GONE
            }
            
            // Click listeners
            root.setOnClickListener {
                onAction(reminder, "view")
            }
            
            root.setOnLongClickListener {
                showContextMenu(reminder)
                true
            }
            
            completeButton.setOnClickListener {
                onAction(reminder, "complete")
            }
        }
    }

    override fun getItemCount() = reminders.size
    
    private fun showContextMenu(reminder: SmartReminderManager.SmartReminder) {
        // Context menu will be handled by Activity
    }
}

private fun SmartReminderManager.SmartReminder.isOverdue(): Boolean {
    if (isCompleted) return false
    return repeatPattern == SmartReminderManager.RepeatPattern.ONCE && triggerTime < System.currentTimeMillis()
}

private fun SmartReminderManager.SmartReminder.isUpcoming(): Boolean {
    if (isCompleted) return false
    val now = System.currentTimeMillis()
    val soon = now + 24 * 60 * 60 * 1000
    return triggerTime in now..soon
}
