package com.example.data.util

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTML → PDF using Android's system print engine (same path as "Print to PDF").
 *
 * Efficiency knobs:
 * - 180 dpi (not 300): sharp enough on phone/screen, far smaller files
 * - post-pass [PdfSizeOptimizer] recompresses any rasterized images
 * - 90s watchdog so conversion never hangs forever
 */
object WebViewPdfPrinter {

    /** Screen/share quality — good balance of clarity vs size. */
    private const val PRINT_DPI = 180

    suspend fun renderHtmlToPdf(
        context: Context,
        html: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        withTimeout(90_000L) {
            withContext(Dispatchers.Main) {
                val webView = WebView(context.applicationContext)
                try {
                    webView.settings.javaScriptEnabled = false
                    webView.settings.useWideViewPort = false
                    webView.settings.loadWithOverviewMode = false
                    // Prefer lower memory decode where supported
                    try {
                        @Suppress("DEPRECATION")
                        webView.settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                    } catch (_: Exception) {
                    }

                    onProgress(0.2f)
                    loadHtmlAndAwaitReady(webView, html)
                    onProgress(0.45f)

                    // A4 @ ~150dpi CSS pixels baseline → force layout so Blink paints
                    val widthPx = 794
                    val heightPx = 1123
                    webView.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
                    )
                    webView.layout(0, 0, widthPx, heightPx)

                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(
                            PrintAttributes.Resolution("vada_pdf", "vada_pdf", PRINT_DPI, PRINT_DPI)
                        )
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
                        .build()

                    val adapter = webView.createPrintDocumentAdapter("vada_conversion")
                    val printInfo = layoutDocument(adapter, attributes)
                    onProgress(0.65f)
                    writeDocument(adapter, printInfo, attributes, outputFile)
                    onProgress(0.85f)
                } finally {
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // Heavy lifting off main thread: recompress any bitmaps WebView embedded
        withContext(Dispatchers.IO) {
            if (outputFile.exists() && outputFile.length() > 80_000L) {
                PdfSizeOptimizer.optimizeFile(outputFile)
            }
            onProgress(1.0f)
        }

        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw IllegalStateException("PDF output is empty — print engine produced no pages")
        }
    }

    private suspend fun loadHtmlAndAwaitReady(webView: WebView, html: String) =
        suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("WebView failed to load HTML: $description")
                        )
                    }
                }
            }
            cont.invokeOnCancellation {
                try {
                    webView.stopLoading()
                } catch (_: Exception) {
                }
            }
            webView.loadDataWithBaseURL(
                "https://local.vada/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }

    private suspend fun layoutDocument(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes
    ): PrintDocumentInfo = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        adapter.onLayout(
            null,
            attributes,
            signal,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    if (cont.isActive) {
                        if (info == null) {
                            cont.resumeWithException(IllegalStateException("Print layout returned null info"))
                        } else {
                            cont.resume(info)
                        }
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Print layout failed: ${error ?: "unknown"}")
                        )
                    }
                }
            },
            null
        )
    }

    private suspend fun writeDocument(
        adapter: PrintDocumentAdapter,
        info: PrintDocumentInfo,
        attributes: PrintAttributes,
        outputFile: File
    ) = suspendCancellableCoroutine { cont ->
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()
        val pfd = try {
            ParcelFileDescriptor.open(
                outputFile,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            )
        } catch (e: Exception) {
            cont.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        val signal = CancellationSignal()
        cont.invokeOnCancellation {
            signal.cancel()
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
        adapter.onWrite(
            arrayOf(PageRange.ALL_PAGES),
            pfd,
            signal,
            object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pages: Array<out PageRange>?) {
                    try {
                        pfd.close()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onWriteFailed(error: CharSequence?) {
                    try {
                        pfd.close()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Print write failed: ${error ?: "unknown"}")
                        )
                    }
                }
            }
        )
    }
}
