package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * مدیر هوشمند یادآورهای تعمیر و نگهداری خودرو
 */
class CarMaintenanceManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("car_maintenance", Context.MODE_PRIVATE)
    private val notificationHelper = NotificationHelper(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    companion object {
        private const val VEHICLES_KEY = "vehicles"
        private const val MAINTENANCE_RECORDS_KEY = "maintenance_records"
        private const val REMINDERS_KEY = "maintenance_reminders"
    }
    
    @Serializable
    data class Vehicle(
        val id: String,
        val brand: String,
        val model: String,
        val year: Int,
        val licensePlate: String,
        val vin: String = "",
        val currentMileage: Long = 0,
        val fuelType: FuelType,
        val transmissionType: TransmissionType,
        val purchaseDate: String,
        val isActive: Boolean = true,
        val imageUrl: String = ""
    )
    
    @Serializable
    data class MaintenanceRecord(
        val id: String,
        val vehicleId: String,
        val type: MaintenanceType,
        val description: String,
        val date: String,
        val mileage: Long,
        val cost: Double,
        val provider: String = "",
        val notes: String = "",
        val nextDueDate: String? = null,
        val nextDueMileage: Long? = null,
        val documents: List<String> = emptyList()
    )
    
    @Serializable
    data class MaintenanceReminder(
        val id: String,
        val vehicleId: String,
        val type: MaintenanceType,
        val title: String,
        val description: String,
        val dueDate: String? = null,
        val dueMileage: Long? = null,
        val reminderInterval: ReminderInterval,
        val isActive: Boolean = true,
        val lastCompletedDate: String? = null,
        val lastCompletedMileage: Long? = null
    )
    
    @Serializable
    enum class FuelType {
        GASOLINE, // بنزین
        DIESEL, // دیزل
        HYBRID, // هیبریدی
        ELECTRIC, // برقی
        CNG // گاز طبیعی
    }
    
    @Serializable
    enum class TransmissionType {
        MANUAL, // دستی
        AUTOMATIC, // اتوماتیک
        CVT, // CVT
        SEMI_AUTOMATIC // نیمه اتوماتیک
    }
    
    @Serializable
    enum class MaintenanceType {
        OIL_CHANGE, // تعویض روغن
        OIL_FILTER, // فیلتر روغن
        AIR_FILTER, // فیلتر هوا
        CABIN_FILTER, // فیلتر کابین
        FUEL_FILTER, // فیلتر سوخت
        SPARK_PLUGS, // شمع‌ها
        BRAKE_PADS, // لنت ترمز
        BRAKE_FLUID, // روغن ترمز
        COOLANT, // ضد یخ
        TRANSMISSION_FLUID, // روغن گیربکس
        TIRE_ROTATION, // چرخش لاستیک‌ها
        TIRE_REPLACEMENT, // تعویض لاستیک
        BATTERY, // باتری
        TIMING_BELT, // تایم بند
        INSPECTION, // بازرسی فنی
        INSURANCE, // بیمه
        OTHER // سایر
    }
    
    @Serializable
    enum class ReminderInterval {
        DAILY, // روزانه
        WEEKLY, // هفتگی
        MONTHLY, // ماهانه
        QUARTERLY, // فصلی
        YEARLY, // سالانه
        MILEAGE_BASED // بر اساس کیلومتر
    }
    
    /**
     * افزودن خودروی جدید
     */
    fun addVehicle(vehicle: Vehicle) {
        try {
            val vehicles = getVehicles().toMutableList()
            vehicles.add(vehicle)
            saveVehicles(vehicles)
            
            // ایجاد یادآورهای پیش‌فرض برای خودرو
            createDefaultReminders(vehicle)
            
            Log.i("CarMaintenanceManager", "✅ خودروی جدید اضافه شد: ${vehicle.brand} ${vehicle.model}")
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در افزودن خودرو: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام خودروها
     */
    fun getVehicles(): List<Vehicle> {
        return try {
            val vehiclesJson = prefs.getString(VEHICLES_KEY, null)
            if (vehiclesJson != null) {
                json.decodeFromString<List<Vehicle>>(vehiclesJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در دریافت خودروها: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت خودروهای فعال
     */
    fun getActiveVehicles(): List<Vehicle> {
        return getVehicles().filter { it.isActive }
    }
    
    /**
     * به‌روزرسانی کیلومتر خودرو
     */
    fun updateVehicleMileage(vehicleId: String, newMileage: Long) {
        try {
            val vehicles = getVehicles().toMutableList()
            val index = vehicles.indexOfFirst { it.id == vehicleId }
            if (index != -1) {
                vehicles[index] = vehicles[index].copy(currentMileage = newMileage)
                saveVehicles(vehicles)
                
                // بررسی یادآورهای بر اساس کیلومتر
                checkMileageBasedReminders(vehicleId, newMileage)
                
                Log.i("CarMaintenanceManager", "✅ کیلومتر خودرو به‌روزرسانی شد: $newMileage")
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در به‌روزرسانی کیلومتر خودرو: ${e.message}")
        }
    }
    
    /**
     * افزودن سرویس نگهداری
     */
    fun addMaintenanceRecord(record: MaintenanceRecord) {
        try {
            val records = getMaintenanceRecords().toMutableList()
            records.add(record)
            saveMaintenanceRecords(records)
            
            // به‌روزرسانی یادآور مربوطه
            updateReminderAfterMaintenance(record)
            
            Log.i("CarMaintenanceManager", "✅ رکورد نگهداری اضافه شد: ${record.type}")
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در افزودن رکورد نگهداری: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام رکوردهای نگهداری
     */
    fun getMaintenanceRecords(): List<MaintenanceRecord> {
        return try {
            val recordsJson = prefs.getString(MAINTENANCE_RECORDS_KEY, null)
            if (recordsJson != null) {
                json.decodeFromString<List<MaintenanceRecord>>(recordsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در دریافت رکوردهای نگهداری: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت رکوردهای نگهداری یک خودرو
     */
    fun getVehicleMaintenanceRecords(vehicleId: String): List<MaintenanceRecord> {
        return getMaintenanceRecords().filter { it.vehicleId == vehicleId }
            .sortedByDescending { it.date }
    }
    
    /**
     * افزودن یادآور نگهداری
     */
    fun addMaintenanceReminder(reminder: MaintenanceReminder) {
        try {
            val reminders = getMaintenanceReminders().toMutableList()
            reminders.add(reminder)
            saveMaintenanceReminders(reminders)
            
            // شروع بررسی دوره‌ای یادآورها
            startPeriodicReminderCheck()
            
            Log.i("CarMaintenanceManager", "✅ یادآور نگهداری اضافه شد: ${reminder.title}")
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در افزودن یادآور نگهداری: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام یادآورهای نگهداری
     */
    fun getMaintenanceReminders(): List<MaintenanceReminder> {
        return try {
            val remindersJson = prefs.getString(REMINDERS_KEY, null)
            if (remindersJson != null) {
                json.decodeFromString<List<MaintenanceReminder>>(remindersJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در دریافت یادآورهای نگهداری: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت یادآورهای فعال
     */
    fun getActiveReminders(): List<MaintenanceReminder> {
        return getMaintenanceReminders().filter { it.isActive }
    }
    
    /**
     * دریافت یادآورهای سررسید شده
     */
    fun getDueReminders(): List<MaintenanceReminder> {
        val today = dateFormat.format(Date())
        val activeVehicles = getActiveVehicles()
        val vehicleMileageMap = activeVehicles.associateBy { it.id }.mapValues { it.value.currentMileage }
        
        return getActiveReminders().filter { reminder ->
            val isDateDue = reminder.dueDate?.let { dueDate ->
                getDaysBetween(today, dueDate) <= 0
            } ?: false
            
            val isMileageDue = reminder.dueMileage?.let { dueMileage ->
                val currentMileage = vehicleMileageMap[reminder.vehicleId] ?: 0L
                currentMileage >= dueMileage
            } ?: false
            
            isDateDue || isMileageDue
        }
    }
    
    /**
     * تکمیل یادآور نگهداری
     */
    fun completeMaintenanceReminder(reminderId: String, completionDate: String, completionMileage: Long) {
        try {
            val reminders = getMaintenanceReminders().toMutableList()
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index != -1) {
                val reminder = reminders[index]
                
                // محاسبه تاریخ سررسید بعدی
                val nextDueDate = calculateNextDueDate(reminder, completionDate)
                val nextDueMileage = calculateNextDueMileage(reminder, completionMileage)
                
                reminders[index] = reminder.copy(
                    lastCompletedDate = completionDate,
                    lastCompletedMileage = completionMileage,
                    dueDate = nextDueDate,
                    dueMileage = nextDueMileage
                )
                
                saveMaintenanceReminders(reminders)
                
                // ارسال نوتیفیکیشن تکمیل
                sendCompletionNotification(reminder)
                
                Log.i("CarMaintenanceManager", "✅ یادآور نگهداری تکمیل شد: ${reminder.title}")
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در تکمیل یادآور نگهداری: ${e.message}")
        }
    }
    
    /**
     * دریافت هزینه‌های نگهداری
     */
    fun getMaintenanceCosts(vehicleId: String? = null): MaintenanceCostSummary {
        val records = if (vehicleId != null) {
            getVehicleMaintenanceRecords(vehicleId)
        } else {
            getMaintenanceRecords()
        }
        
        val totalCost = records.sumOf { it.cost }
        val costByType = records.groupBy { it.type }
            .mapValues { it.value.sumOf { record -> record.cost } }
        
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val thisYearCosts = records.filter { record ->
            val calendar = Calendar.getInstance()
            calendar.time = dateFormat.parse(record.date) ?: Date()
            calendar.get(Calendar.YEAR) == currentYear
        }.sumOf { it.cost }
        
        return MaintenanceCostSummary(
            totalCost = totalCost,
            costByType = costByType,
            thisYearCost = thisYearCosts,
            averageMonthlyCost = thisYearCosts / 12.0
        )
    }
    
    @Serializable
    data class MaintenanceCostSummary(
        val totalCost: Double,
        val costByType: Map<MaintenanceType, Double>,
        val thisYearCost: Double,
        val averageMonthlyCost: Double
    )
    
    /**
     * دریافت توصیه‌های نگهداری
     */
    fun getMaintenanceRecommendations(vehicleId: String): List<String> {
        val vehicle = getVehicles().find { it.id == vehicleId } ?: return emptyList()
        val records = getVehicleMaintenanceRecords(vehicleId)
        val recommendations = mutableListOf<String>()
        
        // بررسی آخرین تعویض روغن
        val lastOilChange = records.filter { it.type == MaintenanceType.OIL_CHANGE }
            .maxByOrNull { it.date }
        
        if (lastOilChange == null || getDaysBetween(lastOilChange.date, dateFormat.format(Date())) > 90) {
            recommendations.add("زمان تعویض روغن موتور فرا رسیده است")
        }
        
        // بررسی لاستیک‌ها
        val lastTireRotation = records.filter { it.type == MaintenanceType.TIRE_ROTATION }
            .maxByOrNull { it.date }
        
        if (lastTireRotation == null || getDaysBetween(lastTireRotation.date, dateFormat.format(Date())) > 180) {
            recommendations.add("زمان چرخش لاستیک‌ها فرا رسیده است")
        }
        
        // بررسی فیلترها
        val lastAirFilter = records.filter { it.type == MaintenanceType.AIR_FILTER }
            .maxByOrNull { it.date }
        
        if (lastAirFilter == null || getDaysBetween(lastAirFilter.date, dateFormat.format(Date())) > 365) {
            recommendations.add("فیلتر هوا نیاز به بررسی دارد")
        }
        
        // توصیه‌های بر اساس کیلومتر
        if (vehicle.currentMileage > 0) {
            when {
                vehicle.currentMileage % 10000 < 1000 -> {
                    recommendations.add("رسیدگی دوره‌ای ۱۰ هزار کیلومتری توصیه می‌شود")
                }
                vehicle.currentMileage % 40000 < 1000 -> {
                    recommendations.add("سرویس بزرگ ۴۰ هزار کیلومتری ضروری است")
                }
                vehicle.currentMileage % 80000 < 1000 -> {
                    recommendations.add("بررسی تایم بند و سایر قطعات مصرفی مهم است")
                }
            }
        }
        
        return recommendations
    }
    
    /**
     * ایجاد یادآورهای پیش‌فرض
     */
    private fun createDefaultReminders(vehicle: Vehicle) {
        val defaultReminders = listOf(
            MaintenanceReminder(
                id = "oil_change_${vehicle.id}",
                vehicleId = vehicle.id,
                type = MaintenanceType.OIL_CHANGE,
                title = "تعویض روغن موتور",
                description = "تعویض روغن و فیلتر روغن هر ۵۰۰۰ کیلومتر یا ۳ ماه",
                dueMileage = 5000,
                reminderInterval = ReminderInterval.MILEAGE_BASED
            ),
            MaintenanceReminder(
                id = "tire_rotation_${vehicle.id}",
                vehicleId = vehicle.id,
                type = MaintenanceType.TIRE_ROTATION,
                title = "چرخش لاستیک‌ها",
                description = "چرخش لاستیک‌ها هر ۱۰۰۰۰ کیلومتر",
                dueMileage = 10000,
                reminderInterval = ReminderInterval.MILEAGE_BASED
            ),
            MaintenanceReminder(
                id = "air_filter_${vehicle.id}",
                vehicleId = vehicle.id,
                type = MaintenanceType.AIR_FILTER,
                title = "تعویض فیلتر هوا",
                description = "تعویض فیلتر هوا هر ۲۰۰۰۰ کیلومتر",
                dueMileage = 20000,
                reminderInterval = ReminderInterval.MILEAGE_BASED
            ),
            MaintenanceReminder(
                id = "inspection_${vehicle.id}",
                vehicleId = vehicle.id,
                type = MaintenanceType.INSPECTION,
                title = "بازرسی فنی سالانه",
                description = "بازرسی فنی سالانه خودرو",
                reminderInterval = ReminderInterval.YEARLY
            )
        )
        
        defaultReminders.forEach { addMaintenanceReminder(it) }
    }
    
    /**
     * شروع بررسی دوره‌ای یادآورها
     */
    private fun startPeriodicReminderCheck() {
        scope.launch {
            while (isActive) {
                checkAndSendReminders()
                delay(24 * 60 * 60 * 1000) // بررسی روزانه
            }
        }
    }
    
    /**
     * بررسی و ارسال یادآورها
     */
    private fun checkAndSendReminders() {
        try {
            val dueReminders = getDueReminders()
            dueReminders.forEach { reminder ->
                sendReminderNotification(reminder)
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در بررسی یادآورها: ${e.message}")
        }
    }
    
    /**
     * بررسی یادآورهای بر اساس کیلومتر
     */
    private fun checkMileageBasedReminders(vehicleId: String, currentMileage: Long) {
        try {
            val reminders = getActiveReminders().filter { 
                it.vehicleId == vehicleId && 
                it.reminderInterval == ReminderInterval.MILEAGE_BASED &&
                it.dueMileage != null
            }
            
            reminders.forEach { reminder ->
                if (currentMileage >= reminder.dueMileage!!) {
                    sendReminderNotification(reminder)
                }
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در بررسی یادآورهای کیلومتری: ${e.message}")
        }
    }
    
    /**
     * ارسال نوتیفیکیشن یادآور
     */
    private fun sendReminderNotification(reminder: MaintenanceReminder) {
        try {
            scope.launch {
                notificationHelper.showNotification(
                    title = "🔧 یادآور نگهداری خودرو",
                    message = reminder.description,
                    channelId = "car_maintenance"
                )
            }
            
            Log.i("CarMaintenanceManager", "✅ نوتیفیکیشن یادآور ارسال شد: ${reminder.title}")
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در ارسال نوتیفیکیشن یادآور: ${e.message}")
        }
    }
    
    /**
     * ارسال نوتیفیکیشن تکمیل
     */
    private fun sendCompletionNotification(reminder: MaintenanceReminder) {
        try {
            scope.launch {
                notificationHelper.showNotification(
                    title = "✅ تکمیل نگهداری خودرو",
                    message = "${reminder.title} با موفقیت انجام شد",
                    channelId = "car_maintenance"
                )
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در ارسال نوتیفیکیشن تکمیل: ${e.message}")
        }
    }
    
    /**
     * محاسبه تاریخ سررسید بعدی
     */
    private fun calculateNextDueDate(reminder: MaintenanceReminder, completionDate: String): String? {
        return if (reminder.reminderInterval != ReminderInterval.MILEAGE_BASED) {
            val calendar = Calendar.getInstance()
            calendar.time = dateFormat.parse(completionDate) ?: Date()
            
            when (reminder.reminderInterval) {
                ReminderInterval.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
                ReminderInterval.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                ReminderInterval.MONTHLY -> calendar.add(Calendar.MONTH, 1)
                ReminderInterval.QUARTERLY -> calendar.add(Calendar.MONTH, 3)
                ReminderInterval.YEARLY -> calendar.add(Calendar.YEAR, 1)
                else -> return null
            }
            
            dateFormat.format(calendar.time)
        } else {
            null
        }
    }
    
    /**
     * محاسبه کیلومتر سررسید بعدی
     */
    private fun calculateNextDueMileage(reminder: MaintenanceReminder, completionMileage: Long): Long? {
        return if (reminder.reminderInterval == ReminderInterval.MILEAGE_BASED) {
            when (reminder.type) {
                MaintenanceType.OIL_CHANGE -> completionMileage + 5000
                MaintenanceType.TIRE_ROTATION -> completionMileage + 10000
                MaintenanceType.AIR_FILTER -> completionMileage + 20000
                MaintenanceType.FUEL_FILTER -> completionMileage + 20000
                MaintenanceType.SPARK_PLUGS -> completionMileage + 20000
                MaintenanceType.TRANSMISSION_FLUID -> completionMileage + 60000
                MaintenanceType.COOLANT -> completionMileage + 40000
                MaintenanceType.TIMING_BELT -> completionMileage + 80000
                else -> null
            }
        } else {
            null
        }
    }
    
    /**
     * به‌روزرسانی یادآور پس از نگهداری
     */
    private fun updateReminderAfterMaintenance(record: MaintenanceRecord) {
        try {
            val reminders = getMaintenanceReminders().toMutableList()
            val index = reminders.indexOfFirst { 
                it.vehicleId == record.vehicleId && it.type == record.type 
            }
            
            if (index != -1) {
                val reminder = reminders[index]
                val nextDueDate = record.nextDueDate
                val nextDueMileage = record.nextDueMileage
                
                reminders[index] = reminder.copy(
                    lastCompletedDate = record.date,
                    lastCompletedMileage = record.mileage,
                    dueDate = nextDueDate,
                    dueMileage = nextDueMileage
                )
                
                saveMaintenanceReminders(reminders)
            }
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در به‌روزرسانی یادآور پس از نگهداری: ${e.message}")
        }
    }
    
    /**
     * محاسبه تعداد روز بین دو تاریخ
     */
    private fun getDaysBetween(startDate: String, endDate: String): Int {
        return try {
            val start = dateFormat.parse(startDate) ?: Date()
            val end = dateFormat.parse(endDate) ?: Date()
            val diff = end.time - start.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * ذخیره خودروها
     */
    private fun saveVehicles(vehicles: List<Vehicle>) {
        try {
            val vehiclesJson = json.encodeToString(vehicles)
            prefs.edit()
                .putString(VEHICLES_KEY, vehiclesJson)
                .apply()
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در ذخیره خودروها: ${e.message}")
        }
    }
    
    /**
     * ذخیره رکوردهای نگهداری
     */
    private fun saveMaintenanceRecords(records: List<MaintenanceRecord>) {
        try {
            val recordsJson = json.encodeToString(records)
            prefs.edit()
                .putString(MAINTENANCE_RECORDS_KEY, recordsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در ذخیره رکوردهای نگهداری: ${e.message}")
        }
    }
    
    /**
     * ذخیره یادآورهای نگهداری
     */
    private fun saveMaintenanceReminders(reminders: List<MaintenanceReminder>) {
        try {
            val remindersJson = json.encodeToString(reminders)
            prefs.edit()
                .putString(REMINDERS_KEY, remindersJson)
                .apply()
        } catch (e: Exception) {
            Log.e("CarMaintenanceManager", "❌ خطا در ذخیره یادآورهای نگهداری: ${e.message}")
        }
    }
    
    /**
     * پاک‌سازی منابع
     */
    fun cleanup() {
        scope.cancel()
        Log.i("CarMaintenanceManager", "🧹 منابع CarMaintenanceManager پاک‌سازی شد")
    }
}
