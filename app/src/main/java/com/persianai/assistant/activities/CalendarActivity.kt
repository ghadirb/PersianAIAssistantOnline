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
            updateAllDates()
            updateMonthDisplay()
            setupGrid()
            setupPrayerTimes()
            setupButtons()
            
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
    }
    
    private fun updateAllDates() {
        try {
            // تاریخ امروز
            val persianDate = PersianDateConverter.getCurrentPersianDate()
            findViewById<TextView>(R.id.persianDateBig)?.text = "${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
            
            // روز هفته
            val weekDays = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = when(calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.SATURDAY -> 0
                java.util.Calendar.SUNDAY -> 1
                java.util.Calendar.MONDAY -> 2
                java.util.Calendar.TUESDAY -> 3
                java.util.Calendar.WEDNESDAY -> 4
                java.util.Calendar.THURSDAY -> 5
                java.util.Calendar.FRIDAY -> 6
                else -> 0
            }
            findViewById<TextView>(R.id.weekDayText)?.text = weekDays[dayOfWeek]
            
            // تاریخ میلادی
            val gregorianFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.ENGLISH)
            findViewById<TextView>(R.id.gregorianDate)?.text = gregorianFormat.format(java.util.Date())
            
            // ماه و سال
            findViewById<TextView>(R.id.monthNameText)?.text = "${PersianDateConverter.getMonthName(persianDate.month)} ماه"
            findViewById<TextView>(R.id.yearText)?.text = "سال ${persianDate.year}"
            
            // مناسبت امروز
            val events = PersianEvents.getEventsForDate(persianDate.month, persianDate.day)
            if (events.isNotEmpty()) {
                findViewById<TextView>(R.id.occasionText)?.text = "📌 ${events[0].title}"
            } else {
                findViewById<TextView>(R.id.occasionText)?.text = "📌 هیچ مناسبتی برای امروز ثبت نشده"
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarActivity", "Error updating dates", e)
        }
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
    
    private fun setupPrayerTimes() {
        try {
            // اوقات شرعی تهران (تقریبی)
            findViewById<TextView>(R.id.fajrTime)?.text = "۰۴:۵۳"
            findViewById<TextView>(R.id.sunriseTime)?.text = "۰۶:۱۷"
            findViewById<TextView>(R.id.dhuhrTime)?.text = "۱۱:۵۹"
            findViewById<TextView>(R.id.sunsetTime)?.text = "۱۷:۴۲"
            findViewById<TextView>(R.id.maghribTime)?.text = "۱۸:۰۰"
            findViewById<TextView>(R.id.midnightTime)?.text = "۲۳:۲۶"
        } catch (e: Exception) {
            android.util.Log.e("CalendarActivity", "Error setting prayer times", e)
        }
    }
    
    private fun setupButtons() {
        try {
            // دکمه اوقات شرعی
            findViewById<com.google.android.material.button.MaterialButton>(R.id.prayerTimesButton)?.setOnClickListener {
                val card = findViewById<com.google.android.material.card.MaterialCardView>(R.id.prayerTimesCard)
                if (card?.visibility == android.view.View.VISIBLE) {
                    card.visibility = android.view.View.GONE
                } else {
                    card?.visibility = android.view.View.VISIBLE
                }
            }
            
            // دکمه تبدیل تاریخ
            findViewById<com.google.android.material.button.MaterialButton>(R.id.convertButton)?.setOnClickListener {
                showDateConverterDialog()
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarActivity", "Error setting up buttons", e)
        }
    }
    
    private fun showDateConverterDialog() {
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        val hijriDate = getHijriDate()
        val message = """
            📅 تاریخ فارسی:
            ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}
            
            📆 تاریخ میلادی:
            ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.ENGLISH).format(java.util.Date())}
            
            🌙 تاریخ قمری:
            $hijriDate
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔄 تبدیل تاریخ")
            .setMessage(message)
            .setPositiveButton("بستن", null)
            .show()
    }
    
    private fun getHijriDate(): String {
        // محاسبه تقریبی تاریخ قمری بر اساس الگوریتم
        val calendar = java.util.Calendar.getInstance()
        val gregorianYear = calendar.get(java.util.Calendar.YEAR)
        val gregorianMonth = calendar.get(java.util.Calendar.MONTH) + 1
        val gregorianDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        
        // فرمول تبدیل میلادی به قمری (تقریبی)
        val totalDays = (gregorianYear - 1) * 365 + gregorianYear / 4 - gregorianYear / 100 + gregorianYear / 400
        val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val daysSinceEpoch = totalDays + monthDays[gregorianMonth - 1] + gregorianDay
        
        // تاریخ مبنا: 1 محرم 1 = 16 ژوئیه 622
        val hijriEpoch = 227015
        val daysSinceHijriEpoch = daysSinceEpoch - hijriEpoch
        
        // هر سال قمری حدود 354.36 روز
        val hijriYear = (daysSinceHijriEpoch / 354.36).toInt() + 1
        val remainingDays = (daysSinceHijriEpoch % 354.36).toInt()
        
        val hijriMonthDays = intArrayOf(30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29)
        var hijriMonth = 1
        var hijriDay = remainingDays
        
        for (i in hijriMonthDays.indices) {
            if (hijriDay <= hijriMonthDays[i]) {
                break
            }
            hijriDay -= hijriMonthDays[i]
            hijriMonth++
        }
        
        if (hijriDay == 0) {
            hijriDay = 1
        }
        
        val hijriMonthNames = arrayOf(
            "محرم", "صفر", "ربیع الاول", "ربیع الثانی", "جمادی الاول", "جمادی الثانی",
            "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
        )
        
        val monthName = if (hijriMonth in 1..12) hijriMonthNames[hijriMonth - 1] else "محرم"
        return "$hijriDay $monthName $hijriYear"
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
