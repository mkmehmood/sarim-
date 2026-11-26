package com.gullzubair.sarim.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
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

    // ---- Ported 1:1 from web `modules/input-guards.js` (messages are identical) ----

    /** Web MAX_AMOUNT (10 billion) used by the strict amount validators. */
    const val WEB_MAX_AMOUNT = 10_000_000_000.0
    const val QTY_MIN = 0.001
    const val QTY_MAX = 1_000_000.0
    const val UNIT_COST_MAX = 100_000_000.0
    const val NAME_MAX = 80

    private val PHONE_CHARS = Regex("^\\+?[\\d\\s\\-()]+$")
    private val ISO_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val STRICT_AMOUNT = Regex("^(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d+)?$")
    private val CONTROL_CHARS = Regex("[\\u0000-\\u001f]")

    /** Empty string = OK, otherwise the message to show. */
    fun phoneIssue(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        if (!PHONE_CHARS.matches(v)) return "Phone number can only contain digits, spaces, dashes and an optional + at the start."
        val digits = v.count { it.isDigit() }
        if (digits < 7 || digits > 15) return "Phone number must have between 7 and 15 digits."
        return ""
    }

    fun dateIssue(raw: String?, today: String? = null): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        val m = ISO_DATE.matchEntire(v) ?: return "Please enter a valid date."
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        val valid = runCatching { LocalDate.of(y, mo, d) }.isSuccess
        if (!valid || y < 2000) return "Please enter a valid date."
        if (!today.isNullOrEmpty() && v > today) return "Date cannot be in the future."
        return ""
    }

    /** Plain number only ("12.5" or "1,234.5"); null when not a plain number. */
    fun strictAmount(raw: String?): Double? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || !STRICT_AMOUNT.matches(s)) return null
        return s.replace(",", "").toDoubleOrNull()
    }

    fun amountIssue(n: Double): String {
        if (!(n > 0.0)) return "Please enter a valid amount greater than 0."
        if (n > WEB_MAX_AMOUNT) return "Amount is too large."
        return ""
    }

    private fun fmtLimit(n: Double): String =
        DecimalFormat("#,##0.###", DecimalFormatSymbols(Locale.US)).format(n)

    /** Range check on an already-parsed number, same wording as [numberIssue]. */
    fun rangeIssue(n: Double, label: String = "Value", min: Double = 0.0, max: Double = Double.POSITIVE_INFINITY, minExclusive: Boolean = false): String {
        if (if (minExclusive) !(n > min) else n < min) {
            return if (minExclusive) "$label must be greater than ${fmtLimit(min)}." else "$label cannot be less than ${fmtLimit(min)}."
        }
        if (n > max) return "$label cannot be more than ${fmtLimit(max)}."
        return ""
    }

    fun numberIssue(
        raw: String?,
        label: String = "Value",
        min: Double = 0.0,
        max: Double = Double.POSITIVE_INFINITY,
        minExclusive: Boolean = false,
        allowBlank: Boolean = false
    ): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return if (allowBlank) "" else "$label is required."
        val n = strictAmount(s) ?: return "$label must be a plain number such as 12.5."
        return rangeIssue(n, label, min, max, minExclusive)
    }

    fun nameIssue(raw: String?, label: String = "Name", max: Int = NAME_MAX): String {
        val s = raw.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (s.length > max) return "$label is too long (at most $max characters)."
        if (CONTROL_CHARS.containsMatchIn(s)) return "$label contains characters that are not allowed."
        return ""
    }

    fun quantityIssue(raw: String?, label: String = "Quantity"): String =
        numberIssue(raw, label, min = QTY_MIN, max = QTY_MAX)

    fun unitCostIssue(raw: String?, label: String = "Cost"): String =
        numberIssue(raw, label, min = 0.01, max = UNIT_COST_MAX)

    /** Reuses the stored spelling when [typed] matches a known name ignoring case and spacing. */
    fun canonicalName(typed: String?, known: Collection<String>): String {
        val clean = typed.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return ""
        val hit = known.firstOrNull { it.replace(Regex("\\s+"), " ").trim().equals(clean, ignoreCase = true) }
        return hit?.replace(Regex("\\s+"), " ")?.trim() ?: clean
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
