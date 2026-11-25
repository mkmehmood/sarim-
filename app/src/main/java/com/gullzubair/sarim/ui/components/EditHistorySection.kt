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
        AlertDialog(
            onDismissRequest = { pendingDeleteIdx = -1 },
            title = { Text("Delete History Entry") },
            text = { Text("Remove this edit log entry permanently from the record?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idx = pendingDeleteIdx
                        pendingDeleteIdx = -1
                        onDeleteEntry(idx)
                    },
                    modifier = Modifier.testTag("confirm_del_edit_history")
                ) { Text("Delete", color = accents.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIdx = -1 }) { Text("Cancel") }
            }
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
    val accents = LocalSarimAccents.current
    val changed = remember(rows) { rows.filter { it.second != it.third } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = accents.emerald) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (changed.isEmpty()) {
                    Text("No field values changed. Save edit stamp anyway?", style = MaterialTheme.typography.bodyMedium)
                } else {
                    changed.forEach { (label, fromVal, toVal) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text("${fromVal.ifBlank { "—" }} → ${toVal.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_edit_diff_btn")) {
                Text("Save Changes", color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DiscardEditDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard Edits?") },
        text = { Text("You are currently editing an entry. Discard unsaved changes and return?") },
        confirmButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("confirm_discard_edit_btn")) {
                Text("Discard", color = accents.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Editing") }
        }
    )
}
