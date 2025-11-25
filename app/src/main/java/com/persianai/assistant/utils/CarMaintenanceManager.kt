package com.persianai.assistant.utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.enums.ServiceType
import com.persianai.assistant.models.MaintenanceRecord

class CarMaintenanceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("car_maintenance", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val recordListType = object : TypeToken<List<MaintenanceRecord>>() {}.type

    fun addRecord(record: MaintenanceRecord) {
        val records = getAllRecords().toMutableList()
        records.add(record)
        saveRecords(records)
    }

    fun getAllRecords(): List<MaintenanceRecord> {
        val json = prefs.getString("maintenance_records", null)
        return if (json != null) gson.fromJson(json, recordListType) else emptyList()
    }

    private fun saveRecords(records: List<MaintenanceRecord>) {
        val json = gson.toJson(records)
        prefs.edit().putString("maintenance_records", json).apply()
    }

    fun getUpcomingServices(days: Int = 7): List<MaintenanceRecord> {
        val now = System.currentTimeMillis()
        val limit = now + days * 24 * 60 * 60 * 1000
        return getAllRecords().filter { it.nextDueDate in now..limit }
    }

    fun getOverdueServices(): List<MaintenanceRecord> {
        return getAllRecords().filter { it.nextDueDate < System.currentTimeMillis() }
    }

    fun getTotalMaintenanceCost(): Double {
        return getAllRecords().sumOf { it.cost }
    }

    fun checkAndNotify() {
        getUpcomingServices(1).forEach { service ->
            NotificationHelper.showReminderNotification(
                context,
                "service_${service.id}",
                "یادآوری سرویس خودرو",
                "${service.type.displayName} برای خودروی شما امروز سررسید می‌شود."
            )
        }
    }
}

class ServiceReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val serviceId = inputData.getString("serviceId") ?: return Result.failure()
        val message = inputData.getString("message") ?: ""
        NotificationHelper.showReminderNotification(applicationContext, "service_$serviceId", "یادآوری سرویس خودرو", message)
        return Result.success()
    }
}
