package com.persianai.assistant.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R
// import حذف شد - استفاده از findViewById
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.PersianEvent
import com.persianai.assistant.utils.PersianEvents
import com.persianai.assistant.adapters.CalendarGridAdapter

class CalendarActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // استفاده از layout یکسان با داشبورد
            setContentView(R.layout.activity_calendar_unified)
            
            val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "📅 تقویم فارسی"
            
            PersianEvents.loadEvents(this)
            setupCalendar()
        } catch (e: Exception) {
            android.util.Log.e("CalendarActivity", "Error in onCreate", e)
            Toast.makeText(this, "خطا در بارگذاری تقویم", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private var currentYear = PersianDateConverter.getCurrentPersianDate().year
    private var currentMonth = PersianDateConverter.getCurrentPersianDate().month
    
    private fun setupCalendar() {
        try {
            updateMonthDisplay()
            setupGrid()
            
            findViewById<android.widget.ImageButton>(R.id.prevMonthButton)?.setOnClickListener {
                currentMonth--
                if (currentMonth < 1) {
                    currentMonth = 12
                    currentYear--
                }
                updateMonthDisplay()
                setupGrid()
            }
            
            findViewById<android.widget.ImageButton>(R.id.nextMonthButton)?.setOnClickListener {
                currentMonth++
                if (currentMonth > 12) {
                    currentMonth = 1
                    currentYear++
                }
                updateMonthDisplay()
                setupGrid()
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarActivity", "Error in setupCalendar", e)
        }
    }
    
    private fun updateMonthDisplay() {
        findViewById<TextView>(R.id.currentMonthText)?.text = "${PersianDateConverter.getMonthName(currentMonth)} $currentYear"
        
        // تاریخ امروز
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        findViewById<TextView>(R.id.persianDateBig)?.text = "${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
    }
    
    private fun getFirstDayOfMonth(persianYear: Int, persianMonth: Int): Int {
        // تبدیل اولین روز ماه فارسی به میلادی
        val persianDate = PersianDateConverter.gregorianToPersian(persianYear, persianMonth, 1)
        
        // پیدا کردن تاریخ میلادی معادل
        var foundDate: java.util.Calendar? = null
        val currentGregorian = java.util.Calendar.getInstance()
        val testCalendar = java.util.Calendar.getInstance()
        
        // جستجوی تقریبی برای پیدا کردن تاریخ میلادی
        for (year in currentGregorian.get(java.util.Calendar.YEAR)-1..currentGregorian.get(java.util.Calendar.YEAR)+1) {
            for (month in 1..12) {
                for (day in 1..28) {
                    val pDate = PersianDateConverter.gregorianToPersian(year, month, day)
                    if (pDate.year == persianYear && pDate.month == persianMonth && pDate.day == 1) {
                        testCalendar.set(year, month - 1, day)
                        foundDate = testCalendar
                        break
                    }
                }
                if (foundDate != null) break
            }
            if (foundDate != null) break
        }
        
        if (foundDate != null) {
            // Java Calendar: SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
            // ما می‌خواهیم: شنبه = 0, یکشنبه = 1, ..., جمعه = 6
            val dayOfWeek = foundDate.get(java.util.Calendar.DAY_OF_WEEK)
            return when(dayOfWeek) {
                java.util.Calendar.SATURDAY -> 0
                java.util.Calendar.SUNDAY -> 1
                java.util.Calendar.MONDAY -> 2
                java.util.Calendar.TUESDAY -> 3
                java.util.Calendar.WEDNESDAY -> 4
                java.util.Calendar.THURSDAY -> 5
                java.util.Calendar.FRIDAY -> 6
                else -> 0
            }
        }
        return 0
    }
    
    private fun setupGrid() {
        try {
        val days = mutableListOf<Int>()
        val daysInMonth = when(currentMonth) {
            in 1..6 -> 31
            in 7..11 -> 30
            else -> 29
        }
        
        // محاسبه روز اول ماه (شنبه = 0, یکشنبه = 1, ... جمعه = 6)
        val firstDayOfMonth = getFirstDayOfMonth(currentYear, currentMonth)
        
        // اضافه کردن cell های خالی برای offset
        for (i in 0 until firstDayOfMonth) {
            days.add(0)  // 0 means empty cell
        }
        
        // اضافه کردن روزهای ماه
        for (i in 1..daysInMonth) {
            days.add(i)
        }
        
        val adapter = CalendarGridAdapter(this, days, currentMonth, currentYear)
        findViewById<android.widget.GridView>(R.id.calendarGrid)?.adapter = adapter
        
        findViewById<android.widget.GridView>(R.id.calendarGrid)?.setOnItemClickListener { _, _, position, _ ->
            val day = days[position]
            if (day > 0) {  // فقط اگر روز معتبر بود
                showDayEvents(day)
            }
        }
        
        val today = PersianDateConverter.getCurrentPersianDate()
        if (currentMonth == today.month && currentYear == today.year) {
            showDayEvents(today.day)
        }
        } catch (e: Exception) {
            android.util.Log.e("CalendarActivity", "Error in setupGrid", e)
        }
    }
    
    private fun showDayEvents(day: Int) {
        val events = PersianEvents.getEventsForDate(currentMonth, day)
        findViewById<TextView>(R.id.occasionText)?.text = if (events.isEmpty()) "📌 مناسبت امروز" else events.joinToString(" | ") { it.title }
        
        // آپدیت تاریخ میلادی
        val gregorianDate = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
        findViewById<TextView>(R.id.gregorianDate)?.text = gregorianDate
        
        if (events.isEmpty()) {
            EventsAdapter(listOf())
        } else {
            EventsAdapter(events)
        }
    }
    
    private fun updateDates() {
        // نمایش تاریخ میلادی
        val calendar = java.util.Calendar.getInstance()
        val gregorianMonth = arrayOf("ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن", 
            "جولای", "آگوست", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
        val gregText = "${calendar.get(java.util.Calendar.DAY_OF_MONTH)} ${gregorianMonth[calendar.get(java.util.Calendar.MONTH)]} ${calendar.get(java.util.Calendar.YEAR)}"
        
        // نمایش تاریخ قمری (تقریبی)
        val hijriText = "13 ربیع الاول 1447"
        
        // اگر TextView ها وجود داشته باشند
        findViewById<android.widget.TextView>(R.id.gregorianDate)?.text = gregText
        findViewById<android.widget.TextView>(R.id.hijriDate)?.text = hijriText
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
