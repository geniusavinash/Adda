package co.mobilise.adda.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns

/**
 * File handling for the Q&A: PDFs are rendered to an image and read by Gemma 3n's
 * vision (works for scanned + text PDFs, reuses the image pipeline); text files
 * are read as plain text. All on-device, no network.
 */
object FileUtils {

    fun mimeOf(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri).orEmpty()

    fun displayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment ?: "file" }
    }

    /** Render the first PDF page to an upright white-backed bitmap for vision. */
    fun renderPdfFirstPage(context: Context, uri: Uri, targetLongest: Int = 1500): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        var renderer: PdfRenderer? = null
        return try {
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return null
            val page = renderer.openPage(0)
            val scale = (targetLongest.toFloat() / maxOf(page.width, page.height)).coerceIn(0.5f, 3f)
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE) // PDFs render transparent where blank
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd.close() }
        }
    }

    fun pdfPageCount(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        } ?: 0
    }.getOrDefault(0)

    /** Read a text file's content (capped) for a text-based document question. */
    fun readTextFile(context: Context, uri: Uri, maxChars: Int = 8000): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText().take(maxChars)
        }
    }.getOrNull()
}
