package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.OverviewEngine
import com.gullzubair.sarim.domain.OverviewFixEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun OverviewFixDialog(
    finding: OverviewEngine.AuditFinding,
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    sequenceStepLabel: String? = null,
    onDismiss: () -> Unit,
    onAdvanceSequence: (() -> Unit)? = null
) {
    val accents = LocalSarimAccents.current
    val today = remember { DateUtils.todayIso() }
    val recMap = remember(snapshot, finding.collection, finding.recordId) {
        snapshotRecordToMap(snapshot, finding.collection, finding.recordId)
    }
    val plan = remember(recMap, finding.codes, snapshot) {
        OverviewFixEngine.planFields(finding.collection, recMap, finding.codes, today, snapshot)
    }

    val fieldValues = remember(finding.id, recMap) { mutableStateMapOf<String, String>() }
    val checkValues = remember(finding.id, recMap) { mutableStateMapOf<String, Boolean>() }
    var validationError by remember(finding.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(finding.id) { mutableStateOf(false) }

    LaunchedEffect(finding.id, plan) {
        fieldValues.clear()
        checkValues.clear()
        plan.fields.forEach { f ->
            if (f.type == "check") {
                checkValues[f.key] = f.defaultChecked
            } else {
                fieldValues[f.key] = f.defaultValue
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(finding.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                val meta = listOfNotNull(
                    finding.label,
                    finding.date.takeIf { it.isNotBlank() },
                    finding.recordId.takeIf { it.isNotBlank() }?.let { "id ${it.take(10)}" },
                    sequenceStepLabel
                ).joinToString(" • ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                finding.reasons.forEach { reason ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (finding.severity == "error") accents.danger.copy(alpha = 0.08f) else accents.gold.copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, if (finding.severity == "error") accents.danger.copy(alpha = 0.3f) else accents.gold.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (finding.severity == "error") accents.danger else accents.gold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                plan.notes.forEach { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }

                plan.fields.forEach { field ->
                    when (field.type) {
                        "check" -> {
                            val checked = checkValues[field.key] ?: field.defaultChecked
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { checkValues[field.key] = it }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(field.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        "select" -> {
                            val currentVal = fieldValues[field.key] ?: field.defaultValue
                            val selectedLabel = field.options.firstOrNull { it.value == currentVal }?.label ?: "Choose..."
                            PickerField(
                                label = field.label.uppercase(),
                                selectedLabel = selectedLabel,
                                items = field.options,
                                itemLabel = { it.label },
                                onSelect = { picked -> fieldValues[field.key] = picked.value },
                                testTag = "ov_fix_select_${field.key}"
                            )
                        }

                        "date" -> {
                            DatePickerField(
                                label = field.label.uppercase(),
                                dateIso = fieldValues[field.key] ?: field.defaultValue,
                                onDateSelected = { pickedDate -> fieldValues[field.key] = pickedDate },
                                testTag = "ov_fix_date_${field.key}"
                            )
                        }

                        else -> {
                            SarimPillInput(
                                value = fieldValues[field.key] ?: field.defaultValue,
                                onValueChange = {
                                    validationError = null
                                    fieldValues[field.key] = it
                                },
                                label = field.label.uppercase(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (field.type == "number") KeyboardType.Decimal else KeyboardType.Text
                                ),
                                testTag = "ov_fix_input_${field.key}"
                            )
                        }
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Text(validationError!!, style = MaterialTheme.typography.bodySmall, color = accents.danger)
                }

                if (plan.deletable) {
                    if (!confirmDelete) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().testTag("ov_fix_delete_btn")
                        ) {
                            Text("Move Duplicate / Broken Copy to Recycle Bin", color = accents.danger)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { confirmDelete = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Keep") }
                            SarimDangerButton(
                                text = "Confirm Delete",
                                onClick = {
                                    vm.deleteAuditFindingRecord(finding.collection, finding.recordId) {
                                        if (onAdvanceSequence != null) onAdvanceSequence() else onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "ov_fix_delete_confirm_btn"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (plan.repair || plan.fields.isNotEmpty() || plan.newId) {
                TextButton(
                    onClick = {
                        if (plan.repair) {
                            vm.applyOverviewFix(finding.collection, finding.recordId, finding.codes, emptyMap()) {
                                if (onAdvanceSequence != null) onAdvanceSequence() else onDismiss()
                            }
                            return@TextButton
                        }
                        val rawMap = buildMap<String, Any?> {
                            plan.fields.forEach { f ->
                                put(f.key, if (f.type == "check") (checkValues[f.key] ?: f.defaultChecked) else fieldValues[f.key].orEmpty())
                            }
                        }
                        val check = OverviewFixEngine.validateValues(plan.fields, rawMap, today)
                        if (!check.ok) {
                            validationError = check.message
                            return@TextButton
                        }
                        vm.applyOverviewFix(finding.collection, finding.recordId, finding.codes, rawMap) {
                            if (onAdvanceSequence != null) onAdvanceSequence() else onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("ov_fix_save_btn")
                ) {
                    Text(
                        when {
                            plan.repair -> "Repair Links"
                            onAdvanceSequence != null -> "Save & Next"
                            else -> "Save Fix"
                        },
                        color = accents.emerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (onAdvanceSequence != null) {
                TextButton(onClick = onAdvanceSequence) {
                    Text("Next", color = accents.emerald, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (onAdvanceSequence != null) {
                    TextButton(onClick = onAdvanceSequence) { Text("Skip") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
fun RepairLinksConfirmDialog(
    affectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val accents = LocalSarimAccents.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repair Payment Links") },
        text = {
            Text(
                "$affectedCount record(s) have unapplied collections or deleted-collection payment links. Repair keeps confirmed allocations, applies leftover collection cash to unpaid sales (newest first), and strips stale deleted links.",
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("ov_repair_links_confirm_btn")) {
                Text("Repair All", color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun snapshotRecordToMap(snapshot: WorkspaceSnapshot, col: String, id: String): Map<String, Any> {
    return when (col) {
        "production" -> snapshot.production.firstOrNull { it.id == id }?.let { WorkspaceJsonCodec.productionToMap(it) } ?: emptyMap()
        "sales" -> snapshot.sales.firstOrNull { it.id == id }?.let { WorkspaceJsonCodec.saleToMap(it) } ?: emptyMap()
        "rep" -> snapshot.repRecords.firstOrNull { it.id == id }?.let { WorkspaceJsonCodec.saleToMap(it) } ?: emptyMap()
        "transactions" -> snapshot.transactions.firstOrNull { it.id == id }?.let { WorkspaceJsonCodec.transactionToMap(it) } ?: emptyMap()
        "expenses" -> snapshot.expenses.firstOrNull { it.id == id }?.let { WorkspaceJsonCodec.expenseToMap(it) } ?: emptyMap()
        "inventory" -> snapshot.inventory.firstOrNull { it.id == id }?.let { WorkspaceJsonCodec.inventoryToMap(it) } ?: emptyMap()
        else -> emptyMap()
    }
}
