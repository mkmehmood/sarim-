package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HISTORY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy • hh:mm a", Locale.US)
    .withZone(ZoneId.systemDefault())

fun formatHistoryTimestamp(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching { HISTORY_FMT.format(Instant.parse(iso)) }.getOrElse {
        iso.take(16).replace("T", " ")
    }
}

fun formatEpochTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return runCatching { HISTORY_FMT.format(Instant.ofEpochMilli(epochMs)) }.getOrDefault("")
}

private fun formatDiffVal(key: String, raw: Any?, masked: Boolean): String {
    if (raw == null) return "—"
    if (raw is Number) {
        val d = raw.toDouble()
        if (d == 0.0 && key != "ledgerPage") return "—"
        if (masked && key in setOf("amount", "totalCost", "totalValue", "unitPrice", "collectedAmount", "received", "fieldExpenses", "commissionPaid", "openingBalance", "oldDebit", "customSalePrice", "purchaseCost", "prevColl", "grossCommission", "commissionPerUnit")) {
            return "•••"
        }
        return InputGuards.fmtNum(d)
    }
    val s = raw.toString().trim()
    return s.ifBlank { "—" }
}

@Composable
fun EditedBadge(isEdited: Boolean, modifier: Modifier = Modifier) {
    if (!isEdited) return
    val accents = LocalSarimAccents.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accents.cyan.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accents.cyan.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Text(
            text = "edited",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accents.cyan,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EditHistorySection(
    editHistory: List<Map<String, Any>>,
    createdAtEpoch: Long = 0L,
    addedLabel: String = "Added",
    canDelete: Boolean = true,
    masked: Boolean = false,
    onDeleteEntry: (Int) -> Unit = {}
) {
    val accents = LocalSarimAccents.current
    var pendingDeleteIdx by remember { mutableIntStateOf(-1) }
    val createdStr = remember(createdAtEpoch) { formatEpochTimestamp(createdAtEpoch) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.History, contentDescription = null, tint = accents.textMuted, modifier = Modifier.size(16.dp))
            Text(
                text = "EDIT HISTORY",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accents.textMuted
            )
        }
        if (editHistory.isEmpty() && createdStr.isBlank()) {
            Text(
                text = "No edits recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = accents.textMuted
            )
        } else {
            editHistory.mapIndexed { idx, h -> idx to h }.reversed().forEach { (idx, h) ->
                val whenStr = formatHistoryTimestamp(h["at"]?.toString().orEmpty())
                val byStr = h["by"]?.toString().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val changes = (h["changes"] as? List<Map<String, Any?>>).orEmpty()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (whenStr.isNotBlank()) {
                                    Text(whenStr, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                                if (byStr.isNotBlank()) {
                                    Text(byStr, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                            if (canDelete) {
                                IconButton(
                                    onClick = { pendingDeleteIdx = idx },
                                    modifier = Modifier.size(40.dp).testTag("edit_history_del_$idx")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete history entry", tint = accents.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        changes.forEach { ch ->
                            val key = ch["f"]?.toString().orEmpty()
                            val label = ch["label"]?.toString()?.ifBlank { key } ?: key
                            val fromTxt = formatDiffVal(key, ch["from"], masked)
                            val toTxt = formatDiffVal(key, ch["to"], masked)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = accents.textMuted, modifier = Modifier.weight(0.42f))
                                Text(
                                    "$fromTxt → $toTxt",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(0.58f)
                                )
                            }
                        }
                    }
                }
            }
            if (createdStr.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accents.softPillFill.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, accents.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(createdStr, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(addedLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = accents.emerald)
                    }
                }
            }
        }
    }

    if (pendingDeleteIdx >= 0) {
        SarimConfirmDialog(
            title = "Delete History Entry?",
            message = "Remove this edit log entry permanently from the record?\n\nThis action cannot be undone.",
            confirmText = "Delete",
            danger = true,
            onConfirm = {
                val idx = pendingDeleteIdx
                pendingDeleteIdx = -1
                onDeleteEntry(idx)
            },
            onDismiss = { pendingDeleteIdx = -1 },
            confirmTag = "confirm_del_edit_history"
        )
    }
}

@Composable
fun EditDiffConfirmDialog(
    title: String = "Confirm Changes",
    rows: List<Triple<String, String, String>>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val changed = remember(rows) { rows.filter { it.second != it.third } }
    val message = remember(changed) {
        if (changed.isEmpty()) "No field values changed. Save edit stamp anyway?\n\nThe existing record will be replaced."
        else changed.joinToString("\n") { (label, from, to) ->
            "$label: ${from.ifBlank { "\u2014" }} \u2192 ${to.ifBlank { "\u2014" }}"
        } + "\n\nThe existing record will be replaced."
    }
    SarimConfirmDialog(
        title = title,
        message = message,
        confirmText = "Update",
        cancelText = "Cancel",
        tone = DialogTone.Warning,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmTag = "confirm_edit_diff_btn"
    )
}

@Composable
fun DiscardEditDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    SarimConfirmDialog(
        title = "Discard Edits?",
        message = "You are currently editing an entry.\nDiscard unsaved changes and return?",
        confirmText = "Discard",
        cancelText = "Keep Editing",
        danger = true,
        onConfirm = onDiscard,
        onDismiss = onDismiss,
        confirmTag = "confirm_discard_edit_btn"
    )
}
