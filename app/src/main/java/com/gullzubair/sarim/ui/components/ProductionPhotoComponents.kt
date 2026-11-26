package com.gullzubair.sarim.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.util.CaptionedPhotoSharer

data class ProdPhotoSlot(
    val existingKey: String? = null,
    val dataUrl: String
)

@Composable
fun DecodedDataUrlImage(
    dataUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bmp: Bitmap? = remember(dataUrl) { CaptionedPhotoSharer.decodeDataUrl(dataUrl) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(LocalSarimAccents.current.inputFill))
    }
}

@Composable
fun ProductionPhotoPickerStrip(
    slots: List<ProdPhotoSlot>,
    maxPhotos: Int = 6,
    onAddClick: () -> Unit,
    onRemoveAt: (Int) -> Unit,
    onPreviewAt: (Int) -> Unit
) {
    val accents = LocalSarimAccents.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onAddClick,
            enabled = slots.size < maxPhotos,
            shape = RoundedCornerShape(14.dp),
            color = accents.softPillFill,
            border = BorderStroke(1.dp, accents.emerald.copy(alpha = if (slots.size < maxPhotos) 0.55f else 0.25f)),
            modifier = Modifier.testTag("prod_attach_photo_btn")
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (slots.isEmpty()) "Attach Scale Photos (Up to $maxPhotos)"
                    else "Scale Photos (${slots.size}/$maxPhotos) • Tap to add",
                    style = MaterialTheme.typography.labelLarge,
                    color = accents.emerald
                )
            }
        }
        if (slots.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slots.forEachIndexed { idx, slot ->
                    Box(modifier = Modifier.size(84.dp)) {
                        DecodedDataUrlImage(
                            dataUrl = slot.dataUrl,
                            contentDescription = "Scale photo ${idx + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreviewAt(idx) }
                                .testTag("prod_photo_thumb_$idx")
                        )
                        IconButton(
                            onClick = { onRemoveAt(idx) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp)
                                .testTag("prod_photo_remove_$idx")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.72f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove photo ${idx + 1}",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionPhotoLightboxDialog(
    photos: List<String>,
    initialIndex: Int = 0,
    record: ProductionRecord? = null,
    currencySymbol: String = "Rs",
    hideMoney: Boolean = false,
    onShareRecord: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    var index by remember(photos, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, photos.lastIndex))
    }
    val accents = LocalSarimAccents.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF00B1120))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (photos.size > 1) "Photo ${index + 1} of ${photos.size}" else "Scale Photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onShareRecord != null) {
                        IconButton(
                            onClick = onShareRecord,
                            modifier = Modifier.size(48.dp).testTag("lightbox_share_btn")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share captioned photo", tint = accents.emerald)
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("lightbox_close_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close lightbox", tint = Color.White)
                    }
                }
            }

            DecodedDataUrlImage(
                dataUrl = photos[index],
                contentDescription = "Full scale photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { index = if (index > 0) index - 1 else photos.lastIndex },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous photo", tint = Color.White)
                        }
                        IconButton(
                            onClick = { index = if (index < photos.lastIndex) index + 1 else 0 },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next photo", tint = Color.White)
                        }
                    }
                }
                if (record != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Date: ${record.date}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            Text(
                                "Gross: ${InputGuards.fmtNum(record.grossWt)} kg  •  Container: ${InputGuards.fmtNum(record.contWt)} kg  •  Net: ${InputGuards.fmtNum(record.net)} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1)
                            )
                            if (!hideMoney) {
                                Text(
                                    "Total Value: ${InputGuards.formatMoney(record.totalSale, currencySymbol)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = accents.emerald
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionMultiShareBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedCount <= 0) return
    val accents = LocalSarimAccents.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accents.cardElevated,
        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selectedCount entry photo(s) selected",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.borderSubtle),
                    modifier = Modifier.testTag("prod_multishare_clear")
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
                Surface(
                    onClick = onShare,
                    shape = RoundedCornerShape(12.dp),
                    color = accents.emerald,
                    modifier = Modifier.testTag("prod_multishare_go")
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }
}
