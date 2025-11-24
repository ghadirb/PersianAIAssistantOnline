package com.persianai.assistant.utils

import java.util.Calendar
import java.util.GregorianCalendar

class JalaliCalendar(year: Int, month: Int, day: Int) {

    private var year: Int = 0
    private var month: Int = 0
    private var day: Int = 0

    init {
        set(year, month, day)
    }

    constructor() : this(0, 0, 0) {
        val calendar = GregorianCalendar()
        val gYear = calendar.get(Calendar.YEAR)
        val gMonth = calendar.get(Calendar.MONTH) + 1
        val gDay = calendar.get(Calendar.DAY_OF_MONTH)
        val j = gregorianToJalali(gYear, gMonth, gDay)
        set(j.year, j.month, j.day)
    }

    fun getYear(): Int = year
    fun getMonth(): Int = month
    fun getDay(): Int = day

    fun set(year: Int, month: Int, day: Int) {
        this.year = year
        this.month = month
        this.day = day
    }

    override fun toString(): String {
        return String.format("%04d/%02d/%02d", getYear(), getMonth(), getDay())
    }

    private fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliCalendar {
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400

        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i + 1]
        }
        if (gm > 1 && (gYear % 4 == 0 && gYear % 100 != 0 || gYear % 400 == 0)) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var i = 0
        while (i < 11 && jDayNo >= jDaysInMonth[i + 1]) {
            jDayNo -= jDaysInMonth[i + 1]
            i++
        }
        val jm = i + 1
        val jd = jDayNo + 1

        return JalaliCalendar(jy, jm, jd)
    }
}
