package com.gullzubair.sarim.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateUtils {
    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    private val DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val MONTH_LABEL_FMT = DateTimeFormatter.ofPattern("MMM yy")

    fun todayIso(): String = LocalDate.now().format(DATE_FMT)

    fun nowTimeStr(): String = LocalTime.now().format(TIME_FMT)

    fun epochToIso(epochMs: Long): String {
        if (epochMs <= 0L) return todayIso()
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
    }

    fun formatDisplay(isoDate: String): String =
        parseIso(isoDate)?.format(DISPLAY_FMT) ?: isoDate

    fun formatPretty(isoDate: String): String = formatDisplay(isoDate)

    fun parseIso(isoDate: String): LocalDate? = runCatching {
        val clean = isoDate.trim().take(10)
        if (clean.length < 10) null else LocalDate.parse(clean, DATE_FMT)
    }.getOrNull()

    fun parseLocalDate(isoDate: String): LocalDate? = parseIso(isoDate)

    fun isValidPastOrTodayDate(isoDate: String, todayIso: String = todayIso()): Boolean {
        val d = parseIso(isoDate) ?: return false
        val t = parseIso(todayIso) ?: LocalDate.now()
        return !d.isAfter(t) && !d.isBefore(LocalDate.of(2000, 1, 1))
    }

    fun daysBetween(fromIso: String, toIso: String = todayIso()): Long {
        val f = parseIso(fromIso) ?: return 0L
        val t = parseIso(toIso) ?: LocalDate.now()
        return ChronoUnit.DAYS.between(f, t)
    }

    fun isInPeriod(dateIso: String, period: String, fyStartMonth: Int = 7): Boolean {
        if (period.equals("all", ignoreCase = true)) return true
        val d = parseIso(dateIso) ?: return false
        val today = LocalDate.now()
        return when (period.lowercase()) {
            "today" -> d == today
            "yesterday" -> d == today.minusDays(1)
            "week" -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today)
            "month" -> d.year == today.year && d.month == today.month
            "last_month" -> {
                val prev = today.minusMonths(1)
                d.year == prev.year && d.month == prev.month
            }
            "fy", "year" -> {
                val m = fyStartMonth.coerceIn(1, 12)
                val fyStartYear = if (today.monthValue >= m) today.year else today.year - 1
                val fyStart = LocalDate.of(fyStartYear, m, 1)
                !d.isBefore(fyStart) && !d.isAfter(today)
            }
            else -> true
        }
    }

    fun isInRange(dateIso: String, fromIso: String, toIso: String): Boolean {
        val d = parseIso(dateIso) ?: return false
        val f = parseIso(fromIso)
        val t = parseIso(toIso)
        if (f != null && d.isBefore(f)) return false
        if (t != null && d.isAfter(t)) return false
        return true
    }

    fun recentMonthKeys(months: Int, now: LocalDate = LocalDate.now()): List<Pair<String, String>> {
        val curYm = YearMonth.from(now)
        return (months - 1 downTo 0).map { offset ->
            val ym = curYm.minusMonths(offset.toLong())
            ym.toString() to ym.atDay(1).format(MONTH_LABEL_FMT)
        }
    }

    fun financialYearStartIso(fyStartMonth: Int = 7, today: LocalDate = LocalDate.now()): String {
        val m = fyStartMonth.coerceIn(1, 12)
        val year = if (today.monthValue >= m) today.year else today.year - 1
        return LocalDate.of(year, m, 1).format(DATE_FMT)
    }
}
