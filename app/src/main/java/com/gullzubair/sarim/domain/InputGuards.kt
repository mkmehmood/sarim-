package com.gullzubair.sarim.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object InputGuards {
    const val MAX_QUANTITY = 1_000_000.0
    const val MAX_AMOUNT = 1_000_000_000.0
    const val MAX_NAME_LEN = 120
    const val MAX_NOTES_LEN = 500
    const val MAX_PHONE_LEN = 32

    fun sanitizeName(raw: String, maxLen: Int = MAX_NAME_LEN): String =
        raw.trim().replace(Regex("\\s+"), " ").take(maxLen)

    fun sanitizeNotes(raw: String, maxLen: Int = MAX_NOTES_LEN): String =
        raw.trim().take(maxLen)

    fun sanitizePhone(raw: String): String =
        raw.trim().filter { it.isDigit() || it in setOf('+', '-', ' ') }.take(MAX_PHONE_LEN)

    fun parsePositiveDouble(raw: String, max: Double = MAX_AMOUNT): Double? {
        val cleaned = raw.trim().replace(",", "")
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (!parsed.isFinite() || parsed <= 0.0 || parsed > max) return null
        return parsed
    }

    fun parseNonNegativeDouble(raw: String, max: Double = MAX_AMOUNT): Double? {
        if (raw.isBlank()) return 0.0
        val cleaned = raw.trim().replace(",", "")
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (!parsed.isFinite() || parsed < 0.0 || parsed > max) return null
        return parsed
    }

    fun parseSignedDouble(raw: String, min: Double = -MAX_AMOUNT, max: Double = MAX_AMOUNT): Double? {
        if (raw.isBlank()) return 0.0
        val cleaned = raw.trim().replace(",", "")
        val parsed = cleaned.toDoubleOrNull() ?: return null
        if (!parsed.isFinite() || parsed < min || parsed > max) return null
        return parsed
    }

    fun round2(value: Double): Double =
        if (!value.isFinite()) 0.0 else (value * 100.0).roundToLong() / 100.0

    fun round3(value: Double): Double =
        if (!value.isFinite()) 0.0 else (value * 1000.0).roundToLong() / 1000.0

    fun round4(value: Double): Double =
        if (!value.isFinite()) 0.0 else (value * 10000.0).roundToLong() / 10000.0

    fun round6(value: Double): Double =
        if (!value.isFinite()) 0.0 else (value * 1_000_000.0).roundToLong() / 1_000_000.0

    /**
     * Exact 1:1 port of web `finance.js` `fmtNum(n, maxDecimals)`:
     * South Asian 3,2,2 comma grouping (`12,34,567.5`) with trailing decimal zeros trimmed.
     */
    fun fmtNum(value: Double, maxDecimals: Int = 2): String {
        if (!value.isFinite()) return "0"
        val dec = maxDecimals.coerceIn(0, 6)
        val factor = 10.0.pow(dec)
        val rounded = (value * factor).roundToLong() / factor
        val neg = rounded < 0.0
        val absVal = abs(rounded)
        val pattern = if (dec == 0) "0" else "0." + "0".repeat(dec)
        val df = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        val formatted = df.format(absVal)
        val parts = formatted.split(".")
        val intPart = parts[0]
        val decPart = parts.getOrNull(1)?.trimEnd('0').orEmpty()

        val groupedInt = if (intPart.length <= 3) {
            intPart
        } else {
            val lastThree = intPart.takeLast(3)
            val rest = intPart.dropLast(3)
            val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
            "$restGrouped,$lastThree"
        }
        val sign = if (neg && absVal > 0.0) "-" else ""
        return if (decPart.isEmpty()) "$sign$groupedInt" else "$sign$groupedInt.$decPart"
    }

    fun formatCurrency(value: Double, symbol: String = "Rs", masked: Boolean = false): String {
        if (masked) return "$symbol ••••"
        return "$symbol ${fmtNum(value, 2)}"
    }

    fun formatMoney(value: Double, symbol: String = "Rs", masked: Boolean = false): String =
        formatCurrency(value, symbol, masked)

    fun formatKg(value: Double): String = "${fmtNum(value, 2)} kg"
}
