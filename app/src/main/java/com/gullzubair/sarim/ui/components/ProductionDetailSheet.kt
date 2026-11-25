package com.gullzubair.sarim.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.PhotoBlob
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.CaptionedPhotoSharer

@Composable
fun ProductionDetailSheet(
    rec: ProductionRecord,
    stores: List<StoreConfig>,
    photoStore: Map<String, PhotoBlob>,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onOpenLightbox: (List<String>, Int, ProductionRecord) -> Unit,
    onStartEdit: (ProductionRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    val storeLabel = FormulaEngine.storeLabelFor(rec.store, stores).ifBlank { rec.store }
    val photos = rec.photoKeys.mapNotNull { k ->
        photoStore[k]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.dataUri
    }
    val canEdit = !rec.isMerged && !rec.isReturn && !rec.isTransfer

    SarimDetailBottomSheet(
        title = storeLabel,
        rightTitle = InputGuards.formatKg(rec.net),
        subtitle = DateUtils.formatPretty(rec.date).uppercase(),
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gross / Container", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text("${InputGuards.formatKg(rec.grossWt)} / ${InputGuards.formatKg(rec.contWt)}", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Formula Units", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text(InputGuards.fmtNum(rec.formulaUnits), style = MaterialTheme.typography.titleSmall)
            }
            if (!masked) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cost / Sale", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text("${InputGuards.formatMoney(rec.totalCost, currency, false)} / ${InputGuards.formatMoney(rec.totalSale, currency, false)}", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Profit", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(rec.profit, currency, false), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
                }
            }
            val note = rec.returnNote.ifBlank { rec.transferNote }
            if (note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scale Photos (${photos.size})", style = MaterialTheme.typography.labelLarge, color = accents.textMuted)
                    Surface(
                        onClick = {
                            val res = CaptionedPhotoSharer.shareProductionPhotos(context, listOf(rec), photoStore, currency, masked)
                            if (res.isFailure) {
                                Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "Share failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.testTag("prod_sheet_share_photos")
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Share Captioned", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.forEachIndexed { idx, uri ->
                        DecodedDataUrlImage(
                            dataUrl = uri,
                            contentDescription = "Scale photo ${idx + 1}",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenLightbox(photos, idx, rec) }
                                .testTag("prod_sheet_photo_$idx")
                        )
                    }
                }
            }
            if (rec.editHistory.isNotEmpty() || rec.createdAt > 0L) {
                Spacer(Modifier.height(14.dp))
                EditHistorySection(
                    editHistory = rec.editHistory,
                    createdAtEpoch = rec.createdAt,
                    addedLabel = "Logged",
                    canDelete = isAdmin,
                    masked = masked,
                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("production", rec.id, idx) }
                )
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SarimDangerButton(
                    text = if (rec.isTransfer) "Recycle Pair" else "Delete",
                    onClick = {
                        if (rec.isTransfer) {
                            vm.recyclePair("production", rec.transferPairId, rec.id, "$storeLabel (${rec.date})")
                        } else {
                            vm.softDeleteRecord(
                                collection = "production",
                                docId = rec.id,
                                displayName = storeLabel,
                                displayDetail = rec.date,
                                displayAmount = InputGuards.formatKg(rec.net),
                                snapshotMap = WorkspaceJsonCodec.productionToMap(rec)
                            )
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(if (canEdit) 0.45f else 1f),
                    testTag = "prod_delete_${rec.id}"
                )
                if (canEdit) {
                    SarimGradientButton(
                        text = "Edit Entry",
                        onClick = {
                            onDismiss()
                            onStartEdit(rec)
                        },
                        modifier = Modifier.weight(0.55f),
                        testTag = "prod_edit_${rec.id}"
                    )
                }
            }
        }
    )
}
