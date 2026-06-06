package co.mobilise.adda.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders [content] as a QR code on a white quiet-zone card (dark modules on
 * white = max scanner contrast even inside the dark app). Generated once with
 * ZXing and cached via remember.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 220,
) {
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    // Encode off the main thread so the host screen's first frame never janks.
    val image by produceState<ImageBitmap?>(initialValue = null, content, px) {
        value = if (content.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) { generateQrBitmap(content, px)?.asImageBitmap() }
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = "Join QR code",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                "QR…",
                color = Color(0xFF1A1A20),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun generateQrBitmap(
    content: String,
    sizePx: Int,
    dark: Int = 0xFF0D0D0F.toInt(),
    light: Int = 0xFFFFFFFF.toInt(),
): Bitmap? = try {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (matrix.get(x, y)) dark else light
        }
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
} catch (_: Exception) {
    null
}
