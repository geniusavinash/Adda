package co.mobilise.adda.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/** Renders an [ExportSession] to a clean, paginated A4 PDF (white page, dark text). */
object PdfExport {

    private const val PAGE_W = 595 // A4 @ 72dpi (points)
    private const val PAGE_H = 842
    private const val MARGIN = 42f

    fun write(session: ExportSession, out: File) {
        val doc = PdfDocument()
        val contentWidth = PAGE_W - 2 * MARGIN

        val title = textPaint(18f, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), Color.rgb(0x1A, 0x1A, 0x20))
        val dateP = textPaint(10f, Typeface.DEFAULT, Color.rgb(0x80, 0x80, 0x88))
        val askerP = textPaint(12f, Typeface.DEFAULT_BOLD, Color.rgb(0xA0, 0x66, 0x00))
        val bodyP = textPaint(11f, Typeface.DEFAULT, Color.rgb(0x22, 0x22, 0x26))
        val codeP = textPaint(10f, Typeface.MONOSPACE, Color.rgb(0x20, 0x20, 0x28))
        val bgPaint = Paint().apply { color = Color.rgb(0xF0, 0xF0, 0xF3); isAntiAlias = true }
        val rulePaint = Paint().apply { color = Color.rgb(0xDD, 0xDD, 0xE2); strokeWidth = 0.8f }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun draw(text: String, paint: TextPaint, bg: Int? = null, indent: Float = 0f, gapAfter: Float = 6f) {
            if (text.isEmpty()) { y += gapAfter; return }
            val width = (contentWidth - indent).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
            var line = 0
            while (line < layout.lineCount) {
                val avail = (PAGE_H - MARGIN) - y
                val top0 = layout.getLineTop(line)
                var endLine = line
                while (endLine < layout.lineCount && (layout.getLineBottom(endLine) - top0) <= avail) {
                    endLine++
                }
                if (endLine == line) {
                    if (y <= MARGIN + 0.5f) endLine = line + 1 // single line taller than page
                    else { newPage(); continue }
                }
                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(endLine - 1)
                val h = (bottom - top).toFloat()
                if (bg != null) {
                    bgPaint.color = bg
                    canvas.drawRoundRect(
                        MARGIN + indent - 4f, y - 3f, MARGIN + indent + width + 4f, y + h + 3f, 6f, 6f, bgPaint,
                    )
                }
                canvas.save()
                canvas.translate(MARGIN + indent, y - top)
                canvas.clipRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += h
                line = endLine
                if (line < layout.lineCount) newPage()
            }
            y += gapAfter
        }

        // ---- header ----
        draw(session.title, title, gapAfter = 2f)
        draw("Adda · ${fmtDate(session.dateMillis)} · ${session.items.size} questions", dateP, gapAfter = 10f)

        // ---- items ----
        session.items.forEachIndexed { i, item ->
            if (y > PAGE_H - MARGIN - 40f) newPage()
            draw("Q${i + 1} · ${item.asker} · ${fmtTime(item.timeMillis)}", askerP, gapAfter = 3f)
            draw(item.question, bodyP, gapAfter = 6f)
            splitFenced(item.answer).forEach { seg ->
                if (seg.first) {
                    if (seg.second.isNotBlank()) draw(seg.second, codeP, bg = bgPaint.color, indent = 6f, gapAfter = 6f)
                } else if (seg.second.isNotBlank()) {
                    draw(seg.second.trim(), bodyP, gapAfter = 4f)
                }
            }
            y += 6f
            if (y < PAGE_H - MARGIN) {
                canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rulePaint)
            }
            y += 12f
        }

        doc.finishPage(page)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
    }

    private fun textPaint(sizePt: Float, tf: Typeface, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePt
            typeface = tf
            this.color = color
        }
}
