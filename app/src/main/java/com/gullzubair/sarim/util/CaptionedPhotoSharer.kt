package com.gullzubair.sarim.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.domain.InputGuards
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 1:1 Android port of web `prod-photos.js`:
 *  - Compresses picked images to JPEG data URLs (max 1280px, quality 80)
 *  - Decodes base64 data URLs to Bitmaps for thumbnails/lightbox
 *  - Stamps `#0F172A` caption footer band onto JPEG and shares via FileProvider
 */
object CaptionedPhotoSharer {

    fun compressUriToDataUrl(
        context: Context,
        uri: Uri,
        maxDim: Int = 1280,
        quality: Int = 80
    ): String? = runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > maxDim * 2) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null
        val scaled = downscaleBitmap(decoded, maxDim)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    }.getOrNull()

    fun decodeDataUrl(dataUrl: String?): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return runCatching {
            val payload = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun downscaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = max(w, h)
        if (longest <= maxDim || longest <= 0) return src
        val ratio = maxDim.toFloat() / longest.toFloat()
        val nw = (w * ratio).roundToInt().coerceAtLeast(1)
        val nh = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun wrapText(paint: Paint, text: String, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { w ->
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current)
                current = w
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    private fun renderCaptionedBitmap(src: Bitmap, captionLines: List<String>): Bitmap {
        val w = minOf(1280, src.width).coerceAtLeast(320)
        val h = (src.height.toFloat() * (w.toFloat() / src.width.coerceAtLeast(1))).roundToInt().coerceAtLeast(180)
        val scaledSrc = if (src.width != w || src.height != h) {
            Bitmap.createScaledBitmap(src, w, h, true)
        } else {
            src
        }

        val pad = (w * 0.03f).roundToInt().toFloat()
        val fontSize = max(20f, w * 0.034f)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 250, 252)
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 250, 252)
            textSize = fontSize * 0.9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val wrappedLines = captionLines.flatMap { wrapText(titlePaint, it, w - pad * 2f) }
        val lineStep = (fontSize * 1.32f)
        val bandH = (pad * 2f + wrappedLines.size * lineStep).roundToInt()

        val result = Bitmap.createBitmap(w, h + bandH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(scaledSrc, 0f, 0f, null)

        val bandPaint = Paint().apply { color = Color.rgb(15, 23, 42) } // #0F172A
        canvas.drawRect(0f, h.toFloat(), w.toFloat(), (h + bandH).toFloat(), bandPaint)

        var y = h + pad + fontSize
        wrappedLines.forEachIndexed { idx, line ->
            canvas.drawText(line, pad, y, if (idx == 0) titlePaint else bodyPaint)
            y += lineStep
        }

        if (scaledSrc !== src) scaledSrc.recycle()
        return result
    }

    fun shareProductionPhotos(
        context: Context,
        records: List<ProductionRecord>,
        photoStore: Map<String, com.gullzubair.sarim.data.model.PhotoBlob>,
        currencySymbol: String,
        hideMoney: Boolean
    ): Result<Int> = runCatching {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val uris = ArrayList<Uri>()
        val summaryBlocks = mutableListOf<String>()

        records.forEach { rec ->
            val photos = rec.photoKeys.mapNotNull { k ->
                photoStore[k]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.dataUri
            }
            if (photos.isEmpty()) return@forEach
            val captionLines = buildList {
                add("Date: ${rec.date}")
                add("Gross Weight: ${InputGuards.fmtNum(rec.grossWt)} kg")
                add("Container: ${InputGuards.fmtNum(rec.contWt)} kg")
                add("Net Weight: ${InputGuards.fmtNum(rec.net)} kg")
                if (!hideMoney) {
                    add("Total Value: ${InputGuards.formatMoney(rec.totalSale, currencySymbol)}")
                }
            }
            summaryBlocks.add(captionLines.joinToString("\n"))

            val safeStore = rec.store.replace(Regex("\\W+"), "").ifBlank { "store" }
            photos.forEachIndexed { idx, dataUrl ->
                val bmp = decodeDataUrl(dataUrl) ?: return@forEachIndexed
                val captioned = renderCaptionedBitmap(bmp, captionLines)
                bmp.recycle()
                val fileName = "production-${rec.date.ifBlank { "entry" }}-$safeStore-${uris.size + 1}.jpg"
                val outFile = File(exportsDir, "${System.currentTimeMillis()}_${idx}_$fileName")
                FileOutputStream(outFile).use { fos ->
                    captioned.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                captioned.recycle()
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile))
            }
        }

        require(uris.isNotEmpty()) { "No photos found for the selected entries." }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Production details", summaryBlocks.joinToString("\n\n")))

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_TEXT, summaryBlocks.firstOrNull().orEmpty())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_TEXT, summaryBlocks.joinToString("\n\n"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooser = Intent.createChooser(intent, "Share Production Photos").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        uris.size
    }
}
