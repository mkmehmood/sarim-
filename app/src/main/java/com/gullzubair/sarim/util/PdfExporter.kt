package com.gullzubair.sarim.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.gullzubair.sarim.domain.DateUtils
import java.io.File
import java.io.FileOutputStream

data class StatementRow(
    val date: String,
    val description: String,
    val debit: String,
    val credit: String,
    val balance: String
)

object PdfExporter {

    fun exportAndShareStatement(
        context: Context,
        title: String,
        subtitle: String,
        rows: List<StatementRow>,
        summaryFooter: String
    ): Result<Unit> = runCatching {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val lineHeight = 20f

        val headerPaint = Paint().apply {
            color = Color.rgb(5, 150, 105)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        val tableHeaderPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(25, 25, 25)
            textSize = 9f
        }
        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 12f

        fun drawHeader() {
            canvas.drawText("GULL & ZUBAIR NASWAR DEALERS", margin, y, headerPaint)
            y += 18f
            canvas.drawText(title, margin, y, tableHeaderPaint)
            y += 14f
            canvas.drawText("$subtitle  •  Generated ${DateUtils.todayIso()}", margin, y, subPaint)
            y += 10f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 16f
            canvas.drawText("Date", margin, y, tableHeaderPaint)
            canvas.drawText("Description", margin + 72f, y, tableHeaderPaint)
            canvas.drawText("Debit", margin + 295f, y, tableHeaderPaint)
            canvas.drawText("Credit", margin + 375f, y, tableHeaderPaint)
            canvas.drawText("Balance", margin + 455f, y, tableHeaderPaint)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 16f
        }

        drawHeader()

        rows.forEach { row ->
            if (y > pageHeight - 70f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
                drawHeader()
            }
            canvas.drawText(row.date.take(10), margin, y, bodyPaint)
            canvas.drawText(row.description.take(36), margin + 72f, y, bodyPaint)
            canvas.drawText(row.debit.take(15), margin + 295f, y, bodyPaint)
            canvas.drawText(row.credit.take(15), margin + 375f, y, bodyPaint)
            canvas.drawText(row.balance.take(15), margin + 455f, y, bodyPaint)
            y += lineHeight
        }

        y += 10f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 18f
        canvas.drawText(summaryFooter, margin, y, tableHeaderPaint)
        doc.finishPage(page)

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(32)
        val outFile = File(exportsDir, "${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Statement PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
