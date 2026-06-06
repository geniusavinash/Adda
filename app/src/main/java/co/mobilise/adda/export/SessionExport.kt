package co.mobilise.adda.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import co.mobilise.adda.ui.qa.ChatMessage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export formats with their file extension + MIME type. */
enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    DOCX(
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Word (.docx)",
    ),
    XLSX(
        "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Excel (.xlsx)",
    ),
}

data class ExportItem(
    val asker: String,
    val question: String,
    val answer: String,
    val timeMillis: Long,
)

data class ExportSession(
    val title: String,
    val dateMillis: Long,
    val items: List<ExportItem>,
)

/** Build an [ExportSession] from the live chat (pairs each question with its answer). */
fun buildSession(messages: List<ChatMessage>, subject: String): ExportSession {
    val items = mutableListOf<ExportItem>()
    var pending: ChatMessage? = null
    for (m in messages) {
        if (!m.isAi) {
            pending = m
        } else {
            val u = pending
            val q = buildString {
                if (!u?.text.isNullOrBlank()) append(u.text)
                if (u?.image != null) append(if (isNotEmpty()) " " else "").append("[image]")
                if (!u?.fileName.isNullOrBlank()) {
                    append(if (isNotEmpty()) " " else "").append("[file: ${u.fileName}]")
                }
            }.ifBlank { m.question }.ifBlank { "(attachment)" }
            items.add(
                ExportItem(
                    asker = u?.author ?: "You",
                    question = q,
                    answer = m.text,
                    timeMillis = (u?.createdAt ?: m.createdAt),
                ),
            )
            pending = null
        }
    }
    return ExportSession(
        title = subject.ifBlank { "Adda Session" },
        dateMillis = System.currentTimeMillis(),
        items = items,
    )
}

/** Writes a session to a file (in cache/exports) and shares it via the system sheet. */
object SessionExporter {

    fun write(context: Context, session: ExportSession, format: ExportFormat): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = session.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(session.dateMillis))
        val out = File(dir, "Adda_${safe}_$stamp.${format.ext}")
        when (format) {
            ExportFormat.PDF -> PdfExport.write(session, out)
            ExportFormat.DOCX -> writeDocx(session, out)
            ExportFormat.XLSX -> writeXlsx(session, out)
        }
        return out
    }

    fun share(context: Context, file: File, format: ExportFormat) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            // ClipData + flag makes the read-grant propagate through the chooser
            // (some OEMs refuse the grant without it -> "cannot issue Uri grant").
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Export session").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    // ---- .docx (minimal OOXML; monospace runs for code) --------------------

    private fun writeDocx(session: ExportSession, out: File) {
        val body = StringBuilder()
        body.append(para(session.title, bold = true, size = 36))
        body.append(para("Date: ${fmtDate(session.dateMillis)}", size = 18, mono = false))
        body.append(para(""))
        session.items.forEachIndexed { i, item ->
            body.append(para("Q${i + 1} · ${item.asker}  ·  ${fmtTime(item.timeMillis)}", bold = true, size = 24))
            body.append(para(item.question))
            splitFenced(item.answer).forEach { seg ->
                if (seg.first) body.append(para(seg.second, mono = true, size = 20))
                else if (seg.second.isNotBlank()) body.append(para(seg.second))
            }
            body.append(para("─────────────"))
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""

        zip(
            out,
            mapOf(
                "[Content_Types].xml" to CONTENT_TYPES_DOCX,
                "_rels/.rels" to RELS_ROOT_DOCX,
                "word/document.xml" to document,
            ),
        )
    }

    private fun para(text: String, bold: Boolean = false, mono: Boolean = false, size: Int = 22): String {
        val rpr = if (bold || mono || size != 22) {
            "<w:rPr>" +
                (if (mono) "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>" else "") +
                (if (bold) "<w:b/>" else "") +
                "<w:sz w:val=\"$size\"/></w:rPr>"
        } else {
            ""
        }
        val runs = text.split("\n").joinToString("<w:r><w:br/></w:r>") { line ->
            "<w:r>$rpr<w:t xml:space=\"preserve\">${esc(line)}</w:t></w:r>"
        }
        return "<w:p>$runs</w:p>"
    }

    // ---- .xlsx (minimal OOXML; inline strings) -----------------------------

    private fun writeXlsx(session: ExportSession, out: File) {
        val rows = StringBuilder()
        rows.append(row(1, listOf("Asker", "Question", "Answer", "Time")))
        session.items.forEachIndexed { i, item ->
            rows.append(
                row(
                    i + 2,
                    listOf(item.asker, item.question, item.answer, fmtTime(item.timeMillis)),
                ),
            )
        }
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""

        zip(
            out,
            mapOf(
                "[Content_Types].xml" to CONTENT_TYPES_XLSX,
                "_rels/.rels" to RELS_ROOT_XLSX,
                "xl/workbook.xml" to WORKBOOK_XLSX,
                "xl/_rels/workbook.xml.rels" to RELS_WORKBOOK_XLSX,
                "xl/worksheets/sheet1.xml" to sheet,
            ),
        )
    }

    private fun row(r: Int, values: List<String>): String {
        val cols = listOf("A", "B", "C", "D")
        val cells = values.mapIndexed { c, v ->
            "<c r=\"${cols[c]}$r\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>"
        }.joinToString("")
        return "<row r=\"$r\">$cells</row>"
    }

    // ---- shared helpers ----------------------------------------------------

    private fun zip(out: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(out)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private const val CONTENT_TYPES_DOCX =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

    private const val RELS_ROOT_DOCX =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

    private const val CONTENT_TYPES_XLSX =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""

    private const val RELS_ROOT_XLSX =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK_XLSX =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Adda" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val RELS_WORKBOOK_XLSX =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
}

internal fun fmtDate(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(ms))

internal fun fmtTime(ms: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(Date(ms))

/** Split an answer into segments: (isCode, text). Fenced ``` blocks become code. */
internal fun splitFenced(text: String): List<Pair<Boolean, String>> {
    if (!text.contains("```")) return listOf(false to text)
    return text.split("```").mapIndexed { i, part ->
        if (i % 2 == 1) {
            // drop a leading language tag line
            val body = part.trimStart('\n')
            val nl = body.indexOf('\n')
            val firstLine = if (nl >= 0) body.substring(0, nl).trim() else ""
            val isLang = firstLine.isNotEmpty() && firstLine.length <= 15 &&
                firstLine.all { it.isLetterOrDigit() || it == '+' || it == '#' }
            true to (if (isLang && nl >= 0) body.substring(nl + 1) else body).trim('\n')
        } else {
            false to part
        }
    }
}
