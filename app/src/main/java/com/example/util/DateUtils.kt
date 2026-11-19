package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class YearMonthKey(val year: Int, val month: Int) {
    fun toIsoPrefix(): String = String.format(Locale.US, "%04d-%02d", year, month)

    fun plusMonths(delta: Int): YearMonthKey {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
        return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun shortLabel(): String {
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "${names[(month - 1).coerceIn(0, 11)]} $year"
    }

    fun monthNameOnly(): String {
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return names[(month - 1).coerceIn(0, 11)]
    }

    fun daysInMonth(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Sunday = 0 .. Saturday = 6
    fun firstDayOfWeekIndex(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }
}

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayIso(): String = isoFormat.format(Calendar.getInstance().time)

    fun yesterdayIso(): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isoFormat.format(cal.time)
    }

    fun currentYearMonth(): YearMonthKey {
        val cal = Calendar.getInstance()
        return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun currentDayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    fun todayTabLabel(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.DAY_OF_MONTH)}.${cal.get(Calendar.MONTH) + 1}."
    }

    fun parseDayOfMonth(dateIso: String): Int {
        return dateIso.split("-").getOrNull(2)?.toIntOrNull() ?: 1
    }

    fun parseYearMonth(dateIso: String): YearMonthKey? {
        val parts = dateIso.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return YearMonthKey(y, m)
    }

    fun dayOfWeekShort(dateIso: String): String {
        return try {
            val date = isoFormat.parse(dateIso) ?: return ""
            SimpleDateFormat("EEE", Locale.US).format(date)
        } catch (_: Exception) {
            ""
        }
    }

    fun monthYearLabel(dateIso: String): String {
        val ym = parseYearMonth(dateIso) ?: return ""
        return ym.shortLabel()
    }

    fun isoFor(year: Int, month: Int, day: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    fun daysUntilDue(dueDayOfMonth: Int): Int {
        if (dueDayOfMonth <= 0) return Int.MAX_VALUE
        val today = currentDayOfMonth()
        return if (dueDayOfMonth >= today) {
            dueDayOfMonth - today
        } else {
            val maxDays = currentYearMonth().daysInMonth()
            (maxDays - today) + dueDayOfMonth
        }
    }
}
