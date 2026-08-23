package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.min

/**
 * Renders HTML to PDF by **internal screenshots** of a headless WebView:
 * each page is a JPEG-compressed bitmap drawn into [PdfDocument].
 *
 * Pros: tighter control over output size (JPEG quality).
 * Cons: text is not selectable; pure raster pages.
 */
object ScreenshotHtmlPdfRenderer {

    private const val PAGE_WIDTH_PX = 794   // ~A4 @ 96dpi CSS
    private const val PAGE_HEIGHT_PX = 1123
    private const val JPEG_QUALITY = 78

    suspend fun renderHtmlToPdf(
        context: Context,
        html: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        withTimeout(120_000L) {
            val fullBitmap = withContext(Dispatchers.Main) {
                captureFullContent(context, html, onProgress)
            }
            try {
                withContext(Dispatchers.IO) {
                    onProgress(0.7f)
                    writeBitmapAsPagedPdf(fullBitmap, outputFile, onProgress)
                    onProgress(0.95f)
                    if (outputFile.exists() && outputFile.length() > 100_000L) {
                        PdfSizeOptimizer.optimizeFile(outputFile, maxImageEdge = 1400, jpegQuality = 72)
                    }
                    onProgress(1.0f)
                }
            } finally {
                if (!fullBitmap.isRecycled) fullBitmap.recycle()
            }
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw IllegalStateException("Screenshot PDF output is empty")
        }
    }

    private suspend fun captureFullContent(
        context: Context,
        html: String,
        onProgress: (Float) -> Unit
    ): Bitmap = suspendCancellableCoroutine { cont ->
        val webView = WebView(context.applicationContext)
        cont.invokeOnCancellation {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (_: Exception) {
            }
        }
        webView.settings.javaScriptEnabled = false
        webView.settings.useWideViewPort = false
        webView.settings.loadWithOverviewMode = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                try {
                    onProgress(0.35f)
                    // Measure full content height
                    webView.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH_PX, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                    )
                    var contentH = webView.measuredHeight.coerceAtLeast(PAGE_HEIGHT_PX)
                    // Cap extreme heights to avoid OOM (max ~15 A4 pages worth)
                    val maxH = PAGE_HEIGHT_PX * 20
                    if (contentH > maxH) contentH = maxH

                    webView.layout(0, 0, PAGE_WIDTH_PX, contentH)
                    onProgress(0.5f)

                    val bmp = Bitmap.createBitmap(PAGE_WIDTH_PX, contentH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    webView.draw(canvas)

                    try {
                        webView.destroy()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resume(bmp)
                } catch (e: Exception) {
                    try {
                        webView.destroy()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("WebView error: $description")
                    )
                }
            }
        }

        webView.loadDataWithBaseURL("https://local.vada/", html, "text/html", "UTF-8", null)
    }

    private fun writeBitmapAsPagedPdf(
        full: Bitmap,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val pageCount = ceil(full.height / PAGE_HEIGHT_PX.toFloat()).toInt().coerceAtLeast(1)
        val pdf = PdfDocument()
        try {
            for (i in 0 until pageCount) {
                val top = i * PAGE_HEIGHT_PX
                val h = min(PAGE_HEIGHT_PX, full.height - top)
                if (h <= 0) break

                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, i + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                // Draw the slice; if shorter than page, white remains below
                val src = android.graphics.Rect(0, top, full.width, top + h)
                val dst = android.graphics.Rect(0, 0, PAGE_WIDTH_PX, h)
                canvas.drawBitmap(full, src, dst, null)
                pdf.finishPage(page)

                onProgress(0.7f + 0.2f * ((i + 1).toFloat() / pageCount))
            }

            // Write via temp JPEG-compressed path is not available in PdfDocument API;
            // pages are already bitmaps. File size controlled by page resolution + optimizer.
            FileOutputStream(outputFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }
}
