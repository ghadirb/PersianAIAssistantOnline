package com.persianai.assistant.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.persianai.assistant.R
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.PersianEvents

class CalendarGridAdapter(
    private val context: Context,
    private val days: List<Int>,
    private val month: Int,
    private val year: Int
) : BaseAdapter() {

    override fun getCount() = days.size

    override fun getItem(position: Int) = days[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_calendar_day, parent, false)
        
        val dayNumber = view.findViewById<TextView>(R.id.dayNumber)
        val eventIndicator = view.findViewById<View>(R.id.eventIndicator)
        val todayCircle = view.findViewById<View>(R.id.todayCircle)
        
        val day = days[position]
        dayNumber.text = day.toString()
        
        // چک کردن اگر امروز است
        val today = PersianDateConverter.getCurrentPersianDate()
        android.util.Log.d("CalendarAdapter", "Checking day=$day, month=$month, year=$year vs today=${today.day}, ${today.month}, ${today.year}")
        if (day == today.day && month == today.month && year == today.year) {
            android.util.Log.d("CalendarAdapter", "TODAY FOUND! Showing circle")
            todayCircle.visibility = View.VISIBLE
            dayNumber.setTextColor(context.getColor(android.R.color.white))
            dayNumber.textSize = 20f
        } else {
            todayCircle.visibility = View.GONE
            dayNumber.setTextColor(context.getColor(android.R.color.black))
            dayNumber.textSize = 18f
        }
        
        // نمایش نشانگر رویداد
        val events = PersianEvents.getEventsForDate(month, day)
        if (events.isNotEmpty()) {
            eventIndicator.visibility = View.VISIBLE
            // قرمز برای تعطیلات
            if (events.any { it.holiday }) {
                eventIndicator.setBackgroundColor(context.getColor(android.R.color.holo_red_dark))
                dayNumber.setTextColor(context.getColor(android.R.color.holo_red_dark))
            } else {
                eventIndicator.setBackgroundColor(context.getColor(android.R.color.holo_blue_dark))
            }
        } else {
            eventIndicator.visibility = View.GONE
        }
        
        return view
    }
}
