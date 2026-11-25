package com.gullzubair.sarim.domain

import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object InputGuards {
    const val MIN_QTY = 0.001
    const val MAX_QTY = 1_000_000.0
    const val MIN_COST = 0.01
    const val MAX_COST = 100_000_000.0
    const val MIN_CONVERSION = 0.001
    const val MAX_CONVERSION = 100_000.0
    const val MAX_NAME_LEN = 80
    const val MAX_NOTES_LEN = 500

    private val moneyFmt = DecimalFormat("#,##0.##")
    private val kgFmt = DecimalFormat("#,##0.###")

    fun round2(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return round(value * 100.0) / 100.0
    }

    fun round3(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return round(value * 1000.0) / 1000.0
    }

    fun parsePositiveDouble(raw: String): Double? {
        val v = raw.trim().replace(",", "").toDoubleOrNull() ?: return null
        return if (v.isFinite() && v > 0.0) v else null
    }

    fun parseNonNegativeDouble(raw: String): Double? {
        val v = raw.trim().replace(",", "").toDoubleOrNull() ?: return null
        return if (v.isFinite() && v >= 0.0) v else null
    }

    fun clampQty(v: Double): Double = if (!v.isFinite()) 0.0 else v.coerceIn(0.0, MAX_QTY)
    fun clampMoney(v: Double): Double = if (!v.isFinite()) 0.0 else v.coerceIn(-MAX_COST, MAX_COST)

    fun sanitizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LEN)
    fun sanitizeNotes(raw: String): String = raw.trim().take(MAX_NOTES_LEN)

    fun canonicalKey(name: String): String = name.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")

    fun sanitizeDocId(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.ifBlank { "doc_${System.currentTimeMillis()}" }.take(120)
    }

    fun formatMoney(amount: Double, currency: String = "Rs", masked: Boolean = false): String {
        if (masked) return "$currency •••••"
        val clean = if (!amount.isFinite()) 0.0 else amount
        val sign = if (clean < -0.005) "-" else ""
        return "$sign$currency ${moneyFmt.format(abs(round2(clean)))}"
    }

    fun formatKg(qty: Double): String {
        val clean = if (!qty.isFinite()) 0.0 else qty
        return "${kgFmt.format(round3(clean))} kg"
    }

    fun formatNum(value: Double): String {
        val clean = if (!value.isFinite()) 0.0 else value
        return moneyFmt.format(round2(clean))
    }
}
