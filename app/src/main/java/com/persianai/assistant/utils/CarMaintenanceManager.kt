package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

/**
 * مدیریت هوشمند خودرو و سرویس‌ها
 * شامل: تعویض روغن، سرویس دوره‌ای، لاستیک، بازدید فنی، و هشدارهای زمینه‌محور
 */
class CarMaintenanceManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("car_maintenance_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "CarMaintenance"
        private const val KEY_SERVICES = "services"
        private const val KEY_CAR_INFO = "car_info"
        private const val KEY_CURRENT_KM = "current_km"
    }
    
    /**
     * اطلاعات خودرو
     */
    data class CarInfo(
        val brand: String,
        val model: String,
        val year: Int,
        val plateNumber: String,
        val engineType: String = "بنزینی" // بنزینی، دیزل، هیبریدی، برقی
    )
    
    /**
     * نوع سرویس
     */
    enum class ServiceType(val displayName: String, val intervalKm: Int, val intervalMonths: Int) {
        OIL_CHANGE("تعویض روغن", 5000, 6),
        OIL_FILTER("تعویض فیلتر روغن", 10000, 6),
        AIR_FILTER("تعویض فیلتر هوا", 20000, 12),
        TIRE_ROTATION("چرخش لاستیک", 10000, 6),
        TIRE_REPLACEMENT("تعویض لاستیک", 50000, 24),
        BRAKE_CHECK("بازدید ترمز", 15000, 12),
        BRAKE_FLUID("تعویض روغن ترمز", 40000, 24),
        COOLANT("تعویض کولنت", 40000, 24),
        BATTERY_CHECK("بازدید باتری", 10000, 6),
        BATTERY_REPLACEMENT("تعویض باتری", 80000, 36),
        TIMING_BELT("تسمه تایم", 100000, 60),
        SPARK_PLUGS("شمع", 30000, 24),
        INSPECTION("بازدید فنی معاینه", 10000, 12),
        FULL_SERVICE("سرویس کامل", 10000, 12);
    }
    
    /**
     * سرویس انجام شده یا برنامه‌ریزی شده
     */
    data class ServiceRecord(
        val id: String,
        val type: ServiceType,
        val kmAtService: Int,
        val datePerformed: Long,
        val cost: Long = 0,
        val notes: String = "",
        val isDone: Boolean = true,
        val nextDueKm: Int = 0,
        val nextDueDate: Long = 0
    )
    
    /**
     * افزودن/ویرایش اطلاعات خودرو
     */
    fun setCarInfo(carInfo: CarInfo) {
        val json = gson.toJson(carInfo)
        prefs.edit().putString(KEY_CAR_INFO, json).apply()
        Log.i(TAG, "✅ اطلاعات خودرو ذخیره شد: ${carInfo.brand} ${carInfo.model}")
    }
    
    /**
     * دریافت اطلاعات خودرو
     */
    fun getCarInfo(): CarInfo? {
        val json = prefs.getString(KEY_CAR_INFO, null) ?: return null
        return gson.fromJson(json, CarInfo::class.java)
    }
    
    /**
     * بروزرسانی کیلومتر فعلی
     */
    fun updateCurrentKm(km: Int) {
        prefs.edit().putInt(KEY_CURRENT_KM, km).apply()
        Log.d(TAG, "📍 کیلومتر بروز شد: $km")
        
        // بررسی سرویس‌های سررسید
        checkUpcomingServices()
    }
    
    /**
     * دریافت کیلومتر فعلی
     */
    fun getCurrentKm(): Int {
        return prefs.getInt(KEY_CURRENT_KM, 0)
    }
    
    /**
     * ثبت سرویس انجام شده
     */
    fun addServiceRecord(
        type: ServiceType,
        kmAtService: Int,
        datePerformed: Long = System.currentTimeMillis(),
        cost: Long = 0,
        notes: String = ""
    ): ServiceRecord {
        val nextDueKm = kmAtService + type.intervalKm
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = datePerformed
        calendar.add(java.util.Calendar.MONTH, type.intervalMonths)
        val nextDueDate = calendar.timeInMillis
        
        val record = ServiceRecord(
            id = System.currentTimeMillis().toString(),
            type = type,
            kmAtService = kmAtService,
            datePerformed = datePerformed,
            cost = cost,
            notes = notes,
            isDone = true,
            nextDueKm = nextDueKm,
            nextDueDate = nextDueDate
        )
        
        val services = getAllServices().toMutableList()
        services.add(record)
        saveServices(services)
        
        Log.i(TAG, "✅ سرویس ${type.displayName} ثبت شد (${kmAtService} کیلومتر)")
        
        // برنامه‌ریزی هشدار بعدی
        scheduleServiceReminder(record)
        
        return record
    }
    
    /**
     * دریافت تمام سرویس‌ها
     */
    fun getAllServices(): List<ServiceRecord> {
        val json = prefs.getString(KEY_SERVICES, "[]") ?: "[]"
        val type = object : TypeToken<List<ServiceRecord>>() {}.type
        return gson.fromJson(json, type)
    }
    
    /**
     * دریافت سرویس‌های سررسید نزدیک
     */
    fun getUpcomingServices(): List<ServiceRecord> {
        val currentKm = getCurrentKm()
        val now = System.currentTimeMillis()
        val thirtyDaysLater = now + (30 * 24 * 60 * 60 * 1000)
        
        val services = getAllServices()
        val upcoming = mutableListOf<ServiceRecord>()
        
        // بررسی آخرین سرویس هر نوع
        ServiceType.values().forEach { serviceType ->
            val lastService = services
                .filter { it.type == serviceType && it.isDone }
                .maxByOrNull { it.datePerformed }
            
            if (lastService != null) {
                // بررسی بر اساس کیلومتر
                val kmRemaining = lastService.nextDueKm - currentKm
                
                // بررسی بر اساس تاریخ
                val dateRemaining = lastService.nextDueDate - now
                
                // اگر کمتر از 1000 کیلومتر یا کمتر از 30 روز مانده، به لیست اضافه کن
                if (kmRemaining <= 1000 || dateRemaining <= thirtyDaysLater) {
                    upcoming.add(lastService)
                }
            } else {
                // اگر این سرویس هنوز انجام نشده، پیشنهاد انجام بده
                val plannedRecord = ServiceRecord(
                    id = "planned_${serviceType.name}",
                    type = serviceType,
                    kmAtService = currentKm,
                    datePerformed = now,
                    isDone = false,
                    nextDueKm = currentKm + serviceType.intervalKm,
                    nextDueDate = System.currentTimeMillis()
                )
                upcoming.add(plannedRecord)
            }
        }
        
        return upcoming.sortedBy { 
            if (it.isDone) it.nextDueKm - currentKm else 0
        }
    }
    
    /**
     * دریافت سرویس‌های سررسید گذشته
     */
    fun getOverdueServices(): List<ServiceRecord> {
        val currentKm = getCurrentKm()
        val now = System.currentTimeMillis()
        
        val services = getAllServices()
        val overdue = mutableListOf<ServiceRecord>()
        
        ServiceType.values().forEach { serviceType ->
            val lastService = services
                .filter { it.type == serviceType && it.isDone }
                .maxByOrNull { it.datePerformed }
            
            if (lastService != null) {
                val kmOverdue = currentKm > lastService.nextDueKm
                val dateOverdue = now > lastService.nextDueDate
                
                if (kmOverdue || dateOverdue) {
                    overdue.add(lastService)
                }
            }
        }
        
        return overdue.sortedByDescending { currentKm - it.nextDueKm }
    }
    
    /**
     * بررسی سرویس‌های سررسید و ارسال هشدار
     */
    private fun checkUpcomingServices() {
        val upcoming = getUpcomingServices()
        val overdue = getOverdueServices()
        
        if (upcoming.isNotEmpty()) {
            Log.i(TAG, "⚠️ ${upcoming.size} سرویس سررسید نزدیک دارد")
        }
        
        if (overdue.isNotEmpty()) {
            Log.w(TAG, "🚨 ${overdue.size} سرویس سررسید گذشته!")
            
            // ارسال نوتیفیکیشن
            NotificationHelper.showGeneralNotification(
                context,
                title = "🚨 هشدار: سرویس سررسید گذشته",
                message = "${overdue.size} سرویس باید فوری انجام شود:\n${overdue.take(3).joinToString("\n") { "• ${it.type.displayName}" }}"
            )
        }
    }
    
    /**
     * دریافت هشدارهای زمینه‌محور بر اساس آب و هوا
     */
    fun getWeatherBasedAlerts(temperature: Double, condition: String): List<String> {
        val alerts = mutableListOf<String>()
        
        // هشدارهای سرمایی (زیر 5 درجه)
        if (temperature < 5) {
            alerts.add("🥶 هشدار سرما:\n• باتری را چک کنید (باتری در سرما ضعیف می‌شود)\n• ضد یخ رادیاتور را بررسی کنید\n• لاستیک‌ها را چک کنید (فشار باد در سرما کم می‌شود)")
        }
        
        // هشدارهای گرمایی (بالای 40 درجه)
        if (temperature > 40) {
            alerts.add("🔥 هشدار گرما:\n• سطح آب رادیاتور را چک کنید\n• فشار باد لاستیک‌ها را کاهش دهید\n• از پارک در آفتاب خودداری کنید")
        }
        
        // هشدار برف و یخبندان
        if (condition.contains("snow", ignoreCase = true) || condition.contains("ice", ignoreCase = true)) {
            alerts.add("❄️ هشدار برف:\n• زنجیر چرخ همراه داشته باشید\n• مایع شیشه‌شوی ضد یخ استفاده کنید\n• با سرعت کمتر رانندگی کنید")
        }
        
        // هشدار باران
        if (condition.contains("rain", ignoreCase = true)) {
            alerts.add("🌧️ هشدار باران:\n• تیغه برف‌پاک‌کن را چک کنید\n• سیستم ترمز را بررسی کنید\n• چراغ‌ها را روشن کنید")
        }
        
        return alerts
    }
    
    /**
     * محاسبه هزینه کل سرویس‌ها
     */
    fun getTotalMaintenanceCost(): Long {
        return getAllServices().filter { it.isDone }.sumOf { it.cost }
    }
    
    /**
     * دریافت گزارش سرویس‌ها
     */
    fun getMaintenanceReport(): MaintenanceReport {
        val services = getAllServices().filter { it.isDone }
        val upcoming = getUpcomingServices()
        val overdue = getOverdueServices()
        
        return MaintenanceReport(
            totalServices = services.size,
            totalCost = getTotalMaintenanceCost(),
            lastServiceDate = services.maxOfOrNull { it.datePerformed } ?: 0,
            upcomingServicesCount = upcoming.size,
            overdueServicesCount = overdue.size,
            currentKm = getCurrentKm()
        )
    }
    
    /**
     * ذخیره سرویس‌ها
     */
    private fun saveServices(services: List<ServiceRecord>) {
        val json = gson.toJson(services)
        prefs.edit().putString(KEY_SERVICES, json).apply()
    }
    
    /**
     * برنامه‌ریزی هشدار سرویس
     */
    private fun scheduleServiceReminder(record: ServiceRecord) {
        // هشدار 7 روز قبل از سررسید
        val reminderDate = record.nextDueDate - (7 * 24 * 60 * 60 * 1000)
        val now = System.currentTimeMillis()
        
        if (reminderDate > now) {
            val delay = reminderDate - now
            
            val data = Data.Builder()
                .putString("serviceType", record.type.displayName)
                .putInt("nextDueKm", record.nextDueKm)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<ServiceReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("service_reminder_${record.id}")
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            
            Log.d(TAG, "🔔 هشدار سرویس ${record.type.displayName} تنظیم شد")
        }
    }
    
    data class MaintenanceReport(
        val totalServices: Int,
        val totalCost: Long,
        val lastServiceDate: Long,
        val upcomingServicesCount: Int,
        val overdueServicesCount: Int,
        val currentKm: Int
    )
}

/**
 * Worker برای هشدار سرویس
 */
class ServiceReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        val serviceType = inputData.getString("serviceType") ?: ""
        val nextDueKm = inputData.getInt("nextDueKm", 0)
        
        NotificationHelper.showReminderNotification(
            applicationContext,
            title = "🔧 یادآوری سرویس خودرو",
            message = "$serviceType\nکیلومتر سررسید: ${String.format("%,d", nextDueKm)}\n\n7 روز تا سررسید باقی مانده"
        )
        
        return Result.success()
    }
}
