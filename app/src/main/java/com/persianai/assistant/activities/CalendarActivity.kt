package com.persianai.assistant.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityCalendarBinding
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.PersianEvent
import com.persianai.assistant.utils.PersianEvents
import com.persianai.assistant.adapters.CalendarGridAdapter

class CalendarActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCalendarBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📅 تقویم فارسی"
        
        PersianEvents.loadEvents(this)
        setupCalendar()
    }
    
    private var currentYear = PersianDateConverter.getCurrentPersianDate().year
    private var currentMonth = PersianDateConverter.getCurrentPersianDate().month
    
    private fun setupCalendar() {
        updateMonthDisplay()
        setupGrid()
        
        binding.prevMonthBtn.setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            updateMonthDisplay()
            setupGrid()
        }
        
        binding.nextMonthBtn.setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            updateMonthDisplay()
            setupGrid()
        }
    }
    
    private fun updateMonthDisplay() {
        binding.currentMonthText.text = "${PersianDateConverter.getMonthName(currentMonth)} $currentYear"
    }
    
    private fun setupGrid() {
        val days = mutableListOf<Int>()
        val daysInMonth = when(currentMonth) {
            in 1..6 -> 31
            in 7..11 -> 30
            else -> 29
        }
        
        for (i in 1..daysInMonth) {
            days.add(i)
        }
        
        val adapter = CalendarGridAdapter(this, days, currentMonth)
        binding.calendarGrid.adapter = adapter
        
        binding.calendarGrid.setOnItemClickListener { _, _, position, _ ->
            val day = days[position]
            showDayEvents(day)
        }
        
        val today = PersianDateConverter.getCurrentPersianDate()
        if (currentMonth == today.month && currentYear == today.year) {
            showDayEvents(today.day)
        }
    }
    
    private fun showDayEvents(day: Int) {
        val events = PersianEvents.getEventsForDate(currentMonth, day)
        binding.selectedDayEvents.text = "مناسبت‌های $day ${PersianDateConverter.getMonthName(currentMonth)}:"
        
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.adapter = if (events.isEmpty()) {
            EventsAdapter(listOf())
        } else {
            EventsAdapter(events)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class EventsAdapter(private val events: List<PersianEvent>) : 
    RecyclerView.Adapter<EventsAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val date: TextView = view.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.title.text = "${if (event.holiday) "🔴" else "📅"} ${event.title}"
        holder.date.text = "${event.day} ${PersianDateConverter.getMonthName(event.month)}"
    }
    
    override fun getItemCount() = events.size
}
