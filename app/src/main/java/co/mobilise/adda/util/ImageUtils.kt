package co.mobilise.adda.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Image decode/scale/encode helpers for the multimodal Q&A. All on-device. */
object ImageUtils {

    /** Longest side fed to the model; Gemma 3n re-scales internally anyway. */
    private const val MAX_DIM = 1024

    /** Decode [uri] to a downscaled, upright ARGB_8888 bitmap (model-ready). */
    fun decodeScaled(context: Context, uri: Uri, maxDim: Int = MAX_DIM): Bitmap? {
        val res = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            res.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / (sample * 2) >= maxDim) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = res.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            bmp = applyExifRotation(context, uri, bmp)
            if (bmp.config != Bitmap.Config.ARGB_8888) {
                bmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bmp: Bitmap): Bitmap = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val degrees = when (
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                bmp
            } else {
                val m = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        } ?: bmp
    } catch (_: Throwable) {
        bmp
    }

    /** Create a content:// uri for the camera to write a full-res capture into. */
    fun newCameraImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("cap_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ---- network (CLIENT -> host) path -------------------------------------

    fun toBase64Jpeg(bmp: Bitmap, quality: Int = 80): String {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    fun fromBase64(b64: String): Bitmap? = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    } catch (_: Throwable) {
        null
    }
}
