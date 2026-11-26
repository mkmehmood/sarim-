package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gullzubair.sarim.ui.theme.LocalSarimAccents

/** Mirrors web modules/dialog.js tones. */
enum class DialogTone(val eyebrow: String) {
    Primary("Confirm"), Danger("Permanent"), Warning("Warning"), Error("Error"), Success("Success")
}

data class DialogFact(val key: String, val value: String)

sealed interface DialogBlock {
    data class Facts(val items: List<DialogFact>) : DialogBlock
    data class Points(val items: List<String>) : DialogBlock
    data class Callout(val kind: String, val label: String, val text: String) : DialogBlock
    data class Subhead(val text: String) : DialogBlock
    data class Paragraph(val text: String, val lead: Boolean) : DialogBlock
}

data class ParsedDialogMessage(val blocks: List<DialogBlock>, val figure: DialogFact?)

private val WARN_RE = Regex("warning|insufficient|exceed|over-?collect|overpay|high credit|caution|unsaved|mismatch|cannot|can't|not enough|shortage|short by|already (used|sold|has)|outstanding", RegexOption.IGNORE_CASE)
private val DELETE_RE = Regex("delete|remove|erase|purge|discard|clear|wipe", RegexOption.IGNORE_CASE)
private val BULLET_RE = Regex("^[\u2022\u00B7\u25CF*\\-\u2013]\\s+(.+)$")
private val NOTE_RE = Regex("^(warning|note|caution|important):\\s+(.+)$", RegexOption.IGNORE_CASE)
private val FACT_RE = Regex("^([A-Za-z0-9][A-Za-z0-9 ()/&'.\u2019-]{0,32}):\\s+(.{1,70})$")
private val LEADING_GLYPHS = Regex("^[\\s\u00A0\u2190-\u21FF\u2600-\u27BF\uFE0F]+")
private val FIGURE_PRIORITY = listOf(
    "^new total$", "^total( value| sold)?$", "^amount$", "^total cost$", "^required", "^overpayment$",
    "^collecting$", "^this credit sale$", "^credit sales?$", "^net profit$"
).map { Regex(it, RegexOption.IGNORE_CASE) }

fun pickDialogTone(title: String, message: String, danger: Boolean, tone: DialogTone?): DialogTone = when {
    tone != null -> tone
    danger -> DialogTone.Danger
    WARN_RE.containsMatchIn(title) || Regex("^\\s*(warning|caution)", RegexOption.IGNORE_CASE).containsMatchIn(message) -> DialogTone.Warning
    else -> DialogTone.Primary
}

/** Port of web parseMessage(): turns plain "Key: value" / bullet text into structured blocks. */
fun parseDialogMessage(message: String): ParsedDialogMessage {
    val blocks = mutableListOf<DialogBlock>()
    var facts: MutableList<DialogFact>? = null
    var points: MutableList<String>? = null
    var paragraphs = 0
    fun flush() {
        facts?.let { blocks += DialogBlock.Facts(it) }; facts = null
        points?.let { blocks += DialogBlock.Points(it) }; points = null
    }
    message.replace("\r", "").split("\n").map { it.trim() }.forEach { raw ->
        if (raw.isEmpty()) { flush(); return@forEach }
        if (raw.startsWith("\u26A0")) {
            flush(); blocks += DialogBlock.Callout("warning", "Warning", raw.replace(LEADING_GLYPHS, "")); return@forEach
        }
        if (raw.startsWith("\u21A9") || raw.startsWith("\u2139")) {
            flush(); return@forEach
        }
        val line = raw.replace(LEADING_GLYPHS, "").ifEmpty { raw }
        BULLET_RE.matchEntire(line)?.let {
            if (facts != null) flush()
            (points ?: mutableListOf<String>().also { p -> points = p }).add(it.groupValues[1]); return@forEach
        }
        if (Regex("^this (action )?cannot be undone\\.?$", RegexOption.IGNORE_CASE).matches(line)) {
            flush(); blocks += DialogBlock.Callout("danger", "Permanent", line); return@forEach
        }
        NOTE_RE.matchEntire(line)?.let {
            flush()
            val k = it.groupValues[1]
            if (!k.equals("note", true) && !k.equals("important", true)) {
                blocks += DialogBlock.Callout("warning", k.replaceFirstChar { c -> c.uppercase() }.let { s -> s[0] + s.substring(1).lowercase() }, it.groupValues[2])
            }
            return@forEach
        }
        FACT_RE.matchEntire(line)?.let {
            if (!Regex("^https?$", RegexOption.IGNORE_CASE).matches(it.groupValues[1])) {
                if (points != null) flush()
                (facts ?: mutableListOf<DialogFact>().also { f -> facts = f }).add(DialogFact(it.groupValues[1], it.groupValues[2])); return@forEach
            }
        }
        flush()
        if (line.endsWith(":")) { blocks += DialogBlock.Subhead(line); return@forEach }
        blocks += DialogBlock.Paragraph(line, paragraphs == 0 && blocks.isEmpty())
        paragraphs++
    }
    flush()
    var figure: DialogFact? = null
    outer@ for (re in FIGURE_PRIORITY) {
        for (blk in blocks) {
            if (blk !is DialogBlock.Facts) continue
            val list = blk.items
            val idx = list.indexOfFirst { re.containsMatchIn(it.key) && it.value.any(Char::isDigit) && it.value.length <= 18 }
            if (idx >= 0) {
                figure = list[idx]
                val trimmed = list.toMutableList().also { it.removeAt(idx) }
                blocks[blocks.indexOf(blk)] = DialogBlock.Facts(trimmed)
                break@outer
            }
        }
    }
    return ParsedDialogMessage(blocks.filter { it !is DialogBlock.Facts || it.items.isNotEmpty() }, figure)
}

private fun toneIcon(tone: DialogTone, title: String, confirmText: String): ImageVector = when (tone) {
    DialogTone.Danger -> if (DELETE_RE.containsMatchIn("$title $confirmText")) Icons.Default.DeleteOutline else Icons.Default.WarningAmber
    DialogTone.Warning -> Icons.Default.WarningAmber
    DialogTone.Error -> Icons.Default.ErrorOutline
    DialogTone.Success -> Icons.Default.CheckCircleOutline
    DialogTone.Primary -> Icons.Default.HelpOutline
}

/** A tick-box row shown above the buttons (web showGlassConfirm `checkbox`). */
data class DialogCheckbox(val label: String, val checked: Boolean, val confirmTextChecked: String? = null)

/**
 * Android counterpart of web showGlassConfirm / showGlassAlert. Structure:
 * eyebrow chip (top-right) -> gradient glyph -> title -> parsed body -> footer with optional
 * big figure + ghost (cancel) / solid (confirm) buttons.
 */
@Composable
fun SarimConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    danger: Boolean = false,
    tone: DialogTone? = null,
    alertOnly: Boolean = false,
    checkbox: DialogCheckbox? = null,
    onCheckboxChange: (Boolean) -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmTag: String = "confirm_dialog_btn"
) {
    val accents = LocalSarimAccents.current
    val resolved = remember(title, message, danger, tone) { pickDialogTone(title, message, danger, tone) }
    val parsed = remember(message) { parseDialogMessage(message) }
    val (g1, g2) = when (resolved) {
        DialogTone.Success -> accents.emerald to accents.cyan
        DialogTone.Warning -> accents.gold to accents.gold
        DialogTone.Danger -> accents.danger to accents.gold
        DialogTone.Error -> accents.danger to accents.danger
        DialogTone.Primary -> accents.cyan to accents.emerald
    }
    val ink = Color(0xFF03170B)
    val strong = MaterialTheme.colorScheme.onSurface
    val faint = accents.textMuted
    val safeFirst = !alertOnly && resolved == DialogTone.Danger
    val shownConfirm = if (checkbox?.checked == true && checkbox.confirmTextChecked != null) checkbox.confirmTextChecked else confirmText

    Dialog(
        onDismissRequest = { if (!safeFirst) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !safeFirst)
    ) {
        Box(Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.background(Brush.linearGradient(listOf(g1.copy(alpha = 0.10f), Color.Transparent)))) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            Modifier.size(46.dp).background(Brush.linearGradient(listOf(g1, g2)), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(toneIcon(resolved, title, confirmText), null, tint = ink, modifier = Modifier.size(26.dp)) }
                        Surface(shape = RoundedCornerShape(999.dp), color = strong.copy(alpha = 0.12f)) {
                            Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(toneIcon(resolved, title, confirmText), null, tint = g2, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(resolved.eyebrow.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = strong)
                            }
                        }
                    }
                    Text(
                        title.trim(),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).testTag("dialog_title"),
                        style = MaterialTheme.typography.titleLarge,
                        color = strong
                    )
                    Column(
                        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        parsed.blocks.forEach { blk -> DialogBlockView(blk, g1, strong, faint) }
                        checkbox?.let { cb ->
                            Surface(
                                onClick = { onCheckboxChange(!cb.checked) },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(checked = cb.checked, onCheckedChange = onCheckboxChange)
                                    Text(cb.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = strong)
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        parsed.figure?.let { f ->
                            Column {
                                Text(f.key, fontSize = 12.sp, color = faint)
                                Text(f.value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = strong)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!alertOnly) {
                                Surface(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, accents.borderSubtle),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dialog_cancel_btn")
                                ) { Box(contentAlignment = Alignment.Center) { Text(cancelText, fontWeight = FontWeight.Bold, color = strong, maxLines = 1) } }
                            }
                            Surface(
                                onClick = onConfirm,
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Transparent,
                                modifier = Modifier.weight(if (alertOnly) 1f else 3f).heightIn(min = 48.dp).testTag(confirmTag)
                            ) {
                                Box(
                                    Modifier.background(Brush.horizontalGradient(listOf(g1, g2))),
                                    contentAlignment = Alignment.Center
                                ) { Text(shownConfirm, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogBlockView(blk: DialogBlock, g1: Color, strong: Color, faint: Color) {
    val accents = LocalSarimAccents.current
    when (blk) {
        is DialogBlock.Facts -> Column(Modifier.fillMaxWidth()) {
            androidx.compose.material3.HorizontalDivider(color = accents.borderSubtle)
            blk.items.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(f.key, fontSize = 13.sp, color = faint, modifier = Modifier.weight(0.45f))
                    Text(f.value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = strong, textAlign = TextAlign.End, modifier = Modifier.weight(0.55f))
                }
                androidx.compose.material3.HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
            }
        }
        is DialogBlock.Points -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            blk.items.forEach { Row { Text("\u2022 ", color = g1); Text(it, style = MaterialTheme.typography.bodyMedium, color = faint) } }
        }
        is DialogBlock.Callout -> {
            val c = if (blk.kind == "danger") accents.danger else accents.gold
            Surface(shape = RoundedCornerShape(12.dp), color = c.copy(alpha = 0.12f), border = BorderStroke(1.dp, c.copy(alpha = 0.4f))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(blk.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c)
                    Text(blk.text, fontSize = 13.sp, color = strong)
                }
            }
        }
        is DialogBlock.Subhead -> Text(blk.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = strong)
        is DialogBlock.Paragraph -> Text(blk.text, style = MaterialTheme.typography.bodyMedium, color = if (blk.lead) strong else faint)
    }
}

/**
 * Port of web confirmGuard() message building: "Save Production Entry?" / "Update ...?" with
 * detail lines followed by the standard tail sentence (or the admin-approval note for gated sessions).
 */
fun confirmGuardMessage(details: List<Pair<String, String>>, isUpdate: Boolean, gated: Boolean, warning: String? = null): String {
    val lines = details.filter { it.second.isNotBlank() }.map { (k, v) -> "$k: ${if (v.length > 44) v.take(43) + "\u2026" else v}" }
    val tail = when {
        gated -> "The admin must approve it before it becomes part of the shared records."
        isUpdate -> "The existing record will be replaced."
        else -> "Please review the details before continuing."
    }
    val parts = mutableListOf<String>()
    if (lines.isNotEmpty()) { parts += lines.joinToString("\n"); parts += "" }
    parts += tail
    if (warning != null) parts += "Warning: $warning"
    return parts.joinToString("\n")
}

fun confirmGuardTitle(label: String, isUpdate: Boolean, gated: Boolean, verb: String = "Save"): String =
    if (gated) "${if (isUpdate) "Send Update for" else "Send for"} Approval: $label?" else "${if (isUpdate) "Update" else verb} $label?"

fun confirmGuardConfirmWord(isUpdate: Boolean, gated: Boolean, verb: String = "Save"): String =
    if (gated) (if (isUpdate) "Send Update for Approval" else "Send for Approval") else if (isUpdate) "Update" else verb

/**
 * One pending confirmation for an action. Hold it in `remember { mutableStateOf<ConfirmRequest?>(null) }`,
 * set it from the button's onClick, and render it once with [ConfirmRequestHost]. This is how every
 * Save / Delete / Approve / Transfer action asks "are you sure?" the same way the web showGlassConfirm does.
 */
data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel",
    val danger: Boolean = false,
    val tone: DialogTone? = null,
    val tag: String = "confirm_dialog_btn",
    val alertOnly: Boolean = false,
    val onConfirm: () -> Unit = {}
)

@Composable
fun ConfirmRequestHost(request: ConfirmRequest?, onClear: () -> Unit) {
    if (request == null) return
    SarimConfirmDialog(
        title = request.title,
        message = request.message,
        confirmText = request.confirmText,
        cancelText = request.cancelText,
        danger = request.danger,
        tone = request.tone,
        alertOnly = request.alertOnly,
        onConfirm = {
            onClear()
            request.onConfirm()
        },
        onDismiss = onClear,
        confirmTag = request.tag
    )
}

/** Builds the web-style delete prompt: detail lines, then the permanent / recycle-bin note. */
fun deleteConfirmMessage(details: List<Pair<String, String>>, recycleBin: Boolean = true): String {
    val lines = details.filter { it.second.isNotBlank() }.map { (k, v) -> "$k: ${if (v.length > 44) v.take(43) + "\u2026" else v}" }
    val tail = if (recycleBin) "It will be moved to the Recycle Bin." else "This action cannot be undone."
    return if (lines.isEmpty()) tail else lines.joinToString("\n") + "\n\n" + tail
}

/** Builds a "Save X?" prompt for forms that do not have a dedicated confirmation composable. */
fun saveConfirmRequest(
    label: String,
    details: List<Pair<String, String>>,
    gated: Boolean,
    isUpdate: Boolean = false,
    verb: String = "Save",
    warning: String? = null,
    tag: String = "confirm_dialog_btn",
    onConfirm: () -> Unit
): ConfirmRequest = ConfirmRequest(
    title = confirmGuardTitle(label, isUpdate, gated, verb),
    message = confirmGuardMessage(details, isUpdate, gated, warning),
    confirmText = confirmGuardConfirmWord(isUpdate, gated, verb),
    tone = if (warning != null) DialogTone.Warning else DialogTone.Primary,
    tag = tag,
    onConfirm = onConfirm
)

/** Web notifyBlocking(): a one-button message. Tone defaults to Warning; pass DialogTone.Error for hard failures. */
fun blockingAlert(title: String, message: String, tone: DialogTone = DialogTone.Warning, confirmText: String = "OK"): ConfirmRequest =
    ConfirmRequest(title = title, message = message, confirmText = confirmText, tone = tone, alertOnly = true)
