package com.persianai.assistant.utils

import java.util.*

object PersianDateConverter {
    
    private val persianMonths = arrayOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )
    
    fun getCurrentPersianDate(): PersianDate {
        val calendar = Calendar.getInstance()
        return gregorianToPersian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    fun gregorianToPersian(gy: Int, gm: Int, gd: Int): PersianDate {
        var jy: Int
        var jm: Int
        var jd: Int
        
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + 
                   ((gy2 + 399) / 400) + gd + g_d_m[gm - 1]
        
        jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + (days % 31)
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + ((days - 186) % 30)
        }
        
        return PersianDate(jy, jm, jd)
    }
    
    fun getMonthName(month: Int): String {
        return if (month in 1..12) persianMonths[month - 1] else ""
    }
}

data class PersianDate(
    val year: Int,
    val month: Int,
    val day: Int
) {
    override fun toString(): String = "$year/$month/$day"
    
    fun toReadableString(): String {
        return "$day ${PersianDateConverter.getMonthName(month)} $year"
    }
}
