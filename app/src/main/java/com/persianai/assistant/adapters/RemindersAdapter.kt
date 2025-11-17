package com.persianai.assistant.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.data.AdvancedReminder
import com.persianai.assistant.databinding.ItemReminderBinding
import com.persianai.assistant.utils.PersianDateConverter
import java.util.*

class RemindersAdapter(
    private val reminders: List<AdvancedReminder>,
    private val onAction: (AdvancedReminder, String) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    inner class ReminderViewHolder(val binding: ItemReminderBinding) : 
        RecyclerView.ViewHolder(binding.root)

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
                AdvancedReminder.ReminderType.TIME_BASED -> "⏰"
                AdvancedReminder.ReminderType.LOCATION_BASED -> "📍"
                AdvancedReminder.ReminderType.CONDITIONAL -> "⚙️"
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
                val persianDate = PersianDateConverter.gregorianToPersian(Date(reminder.triggerTime))
                timeText.text = persianDate
                timeText.visibility = android.view.View.VISIBLE
            } else {
                timeText.visibility = android.view.View.GONE
            }
            
            // Priority color
            val priorityColor = when (reminder.priority) {
                AdvancedReminder.Priority.LOW -> Color.parseColor("#4CAF50")
                AdvancedReminder.Priority.MEDIUM -> Color.parseColor("#FF9800")
                AdvancedReminder.Priority.HIGH -> Color.parseColor("#F44336")
            }
            priorityIndicator.setBackgroundColor(priorityColor)
            
            // Status
            if (reminder.completed) {
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
            categoryChip.text = reminder.category
            
            // Recurring indicator
            if (reminder.isRecurring) {
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
    
    private fun showContextMenu(reminder: AdvancedReminder) {
        // Context menu will be handled by Activity
    }
}
