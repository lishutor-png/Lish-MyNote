package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    private val fullDateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy - HH:mm", Locale("id", "ID"))

    private val supportedDateParsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US)
    )

    fun parseDateToTimestamp(raw: Any?): Long {
        if (raw == null) return System.currentTimeMillis()
        when (raw) {
            is Number -> {
                val num = raw.toLong()
                return if (num < 10_000_000_000L && num > 0) num * 1000L else num
            }
            is String -> {
                val str = raw.trim()
                str.toLongOrNull()?.let { num ->
                    return if (num < 10_000_000_000L && num > 0) num * 1000L else num
                }
                for (parser in supportedDateParsers) {
                    try {
                        val parsed = parser.parse(str)
                        if (parsed != null) return parsed.time
                    } catch (_: Exception) {}
                }
            }
        }
        return System.currentTimeMillis()
    }

    fun formatNoteDateTime(timestamp: Long): String {
        val noteCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        return when {
            isSameDay(noteCal, nowCal) -> {
                "Hari ini, " + timeFormat.format(Date(timestamp))
            }
            isYesterday(noteCal, nowCal) -> {
                "Kemarin, " + timeFormat.format(Date(timestamp))
            }
            noteCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) -> {
                SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")).format(Date(timestamp))
            }
            else -> {
                dateTimeFormat.format(Date(timestamp))
            }
        }
    }

    fun formatFullDateTime(timestamp: Long): String {
        return fullDateFormat.format(Date(timestamp))
    }

    fun formatDateShort(timestamp: Long): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(Date(timestamp))
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(noteCal: Calendar, nowCal: Calendar): Boolean {
        val clone = nowCal.clone() as Calendar
        clone.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(noteCal, clone)
    }
}
