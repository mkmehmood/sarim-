package com.gullzubair.sarim.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateUtils {
    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val prettyFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun todayIso(): String = LocalDate.now().format(isoFmt)

    fun parseLocalDate(iso: String): LocalDate? = try {
        LocalDate.parse(iso.trim().take(10), isoFmt)
    } catch (_: DateTimeParseException) {
        null
    }

    fun formatPretty(iso: String): String {
        val d = parseLocalDate(iso) ?: return iso
        return d.format(prettyFmt)
    }

    fun shiftDays(iso: String, deltaDays: Long): String {
        val d = parseLocalDate(iso) ?: LocalDate.now()
        return d.plusDays(deltaDays).format(isoFmt)
    }

    fun monthsAgoIso(months: Long): String {
        return LocalDate.now().minusMonths(months).format(isoFmt)
    }

    fun startOfWeekIso(): String {
        val today = LocalDate.now()
        return today.minusDays((today.dayOfWeek.value - 1).toLong()).format(isoFmt)
    }

    fun startOfMonthIso(): String = LocalDate.now().withDayOfMonth(1).format(isoFmt)

    fun startOfFinancialYearIso(startMonth: Int): String {
        val m = startMonth.coerceIn(1, 12)
        val today = LocalDate.now()
        val year = if (today.monthValue >= m) today.year else today.year - 1
        return LocalDate.of(year, m, 1).format(isoFmt)
    }

    fun isInRange(dateIso: String, fromIso: String, toIso: String): Boolean {
        val d = parseLocalDate(dateIso) ?: return false
        val from = parseLocalDate(fromIso) ?: return true
        val to = parseLocalDate(toIso) ?: return true
        return !d.isBefore(from) && !d.isAfter(to)
    }

    fun epochToIso(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(isoFmt)
    }
}
