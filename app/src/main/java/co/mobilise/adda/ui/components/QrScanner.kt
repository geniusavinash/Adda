package co.mobilise.adda.ui.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Live camera QR scanner: CameraX preview + ML Kit barcode analysis.
 * Fires [onQr] exactly once with the first decoded QR's raw value.
 *
 * Teardown is careful: the ImageAnalysis is bound to the lifecycle owner (the
 * NavBackStackEntry), so when this leaves composition while that lifecycle is
 * still RESUMED — e.g. a `key()`-driven remount after a failed scan — CameraX
 * would keep feeding frames to a shut-down executor (RejectedExecutionException)
 * and a closed scanner. So on dispose we stop the stream (clearAnalyzer +
 * unbindAll) BEFORE freeing the executor/scanner, and guard the analyzer body.
 *
 * Caller must hold CAMERA permission before composing this.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScanner(
    onQr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onQrLatest by rememberUpdatedState(onQr)

    val handled = remember { AtomicBoolean(false) }
    val closed = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val providerRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val analysisRef = remember { AtomicReference<ImageAnalysis?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            closed.set(true)
            // Stop frames FIRST so CameraX dispatches nothing to the dead
            // executor / closed scanner, then release resources.
            runCatching { analysisRef.get()?.clearAnalyzer() }
            runCatching { providerRef.get()?.unbindAll() }
            analysisExecutor.shutdown()
            runCatching { scanner.close() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                providerRef.set(provider)

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ia ->
                        analysisRef.set(ia)
                        ia.setAnalyzer(analysisExecutor) { proxy ->
                            val media = proxy.image
                            if (media == null || handled.get() || closed.get()) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            runCatching {
                                val image = InputImage.fromMediaImage(
                                    media, proxy.imageInfo.rotationDegrees,
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                                        if (value != null && !closed.get() &&
                                            handled.compareAndSet(false, true)
                                        ) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onQrLatest(value)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }.onFailure { proxy.close() }
                        }
                    }

                if (!closed.get()) {
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}
