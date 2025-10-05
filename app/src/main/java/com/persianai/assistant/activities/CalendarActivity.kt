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
        
        val today = PersianDateConverter.getCurrentPersianDate()
        val todayEvents = PersianEvents.getEventsForDate(today.month, today.day)
        val holidays = PersianEvents.getAllHolidays().take(20)
        
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.adapter = EventsAdapter(todayEvents + holidays)
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
