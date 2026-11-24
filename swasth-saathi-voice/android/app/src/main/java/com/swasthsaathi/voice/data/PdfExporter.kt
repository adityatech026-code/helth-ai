package com.swasthsaathi.voice.data

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a plain, doctor-friendly PDF from the notebook. Uses the system fonts, so Indic scripts render. */
object PdfExporter {
    private const val W = 595; private const val H = 842; private const val M = 36

    fun export(ctx: Context, entries: List<Entry>, trends: List<Trend>, patientName: String?): File {
        val doc = PdfDocument()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create()); var y = M.toFloat(); var n = 1

        fun newPage() { doc.finishPage(page); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); y = M.toFloat() }
        fun draw(text: String, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, W - 2 * M).setLineSpacing(2f, 1f).build()
            if (y + layout.height > H - M) newPage()
            page.canvas.save(); page.canvas.translate(M.toFloat(), y); layout.draw(page.canvas); page.canvas.restore()
            y += layout.height + 6
        }

        draw("Swasth Saathi Voice - Health Notebook", head)
        draw("Patient: ${patientName ?: "-"}    Generated: ${fmt.format(Date())}", body)
        draw("Self-reported data from a voice assistant. Not a medical record or diagnosis.", body)
        y += 6; draw("Trends", head)
        if (trends.isEmpty()) draw("No recurring symptoms yet.", body) else trends.forEach { draw("- ${it.message}", body) }
        y += 6; draw("Log (newest first)", head)
        entries.forEach { e ->
            draw("${fmt.format(Date(e.timestamp))}  [${e.level}]", bold)
            draw("Said: ${e.transcript}", body)
            draw("Symptoms: ${e.symptoms.joinToString { s -> s.replace('_', ' ') + (e.severity[s]?.let { " ($it)" } ?: "") }.ifBlank { "-" }}" +
                if (e.medications.isNotEmpty()) "   Medication mentioned: ${e.medications.joinToString()}" else "", body)
            draw("Advice given: ${e.recommendation}", body)
            y += 4
        }
        doc.finishPage(page)
        val out = File(ctx.cacheDir, "swasth_saathi_notebook.pdf")
        out.outputStream().use { doc.writeTo(it) }; doc.close()
        return out
    }

    /** Opens the share sheet. Requires the FileProvider declared in the manifest (see README). */
    fun share(ctx: Context, pdf: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", pdf)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share with your doctor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
