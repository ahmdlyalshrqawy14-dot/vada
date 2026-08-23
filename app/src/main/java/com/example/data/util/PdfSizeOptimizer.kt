package com.example.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Shrinks PDF output after WebView print without changing layout.
 * WebView print often embeds large bitmaps / high DPI — this recompresses
 * page images to keep the file practical for sharing.
 */
object PdfSizeOptimizer {

    private const val TAG = "PdfSizeOptimizer"

    /**
     * @param maxImageEdge longest side of embedded images after downscale
     * @param jpegQuality 0–100 (72 is a good print-screen compromise)
     * @return true if file was rewritten smaller (or equal and still valid)
     */
    fun optimizeFile(
        input: File,
        output: File = input,
        maxImageEdge: Int = 1400,
        jpegQuality: Int = 72
    ): Boolean {
        if (!input.exists() || input.length() <= 0L) return false
        val originalSize = input.length()
        val temp = File(input.parentFile, "opt_${input.name}")
        try {
            PDDocument.load(input).use { doc ->
                val replaced = mutableSetOf<COSStream>()
                for (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val resources = page.resources ?: continue
                    val names = resources.xObjectNames.toList()
                    for (name in names) {
                        try {
                            val xObject = resources.getXObject(name)
                            if (xObject !is PDImageXObject) continue
                            val oldStream = xObject.cosStream
                            if (oldStream in replaced) continue
                            val bitmap = xObject.image ?: continue
                            val maxDim = maxOf(bitmap.width, bitmap.height)
                            val finalBmp = if (maxDim > maxImageEdge) {
                                val scale = maxImageEdge.toFloat() / maxDim
                                val tw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                                val th = (bitmap.height * scale).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(bitmap, tw, th, true)
                                if (scaled != bitmap) bitmap.recycle()
                                scaled
                            } else bitmap

                            val baos = ByteArrayOutputStream()
                            finalBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 95), baos)
                            if (finalBmp != bitmap) finalBmp.recycle() else bitmap.recycle()

                            val jpegBytes = baos.toByteArray()
                            // Only replace if we actually save space or forced re-encode of huge images
                            if (jpegBytes.size < (oldStream.length ?: Int.MAX_VALUE) || maxDim > maxImageEdge) {
                                val newImage = JPEGFactory.createFromByteArray(doc, jpegBytes)
                                resources.put(name, newImage)
                                replaced.add(oldStream)
                            }
                        } catch (e: Exception) {
                            AppLogger.logSilentFailure(TAG, "تخطي صورة أثناء ضغط PDF", e)
                        }
                    }
                }
                FileOutputStream(temp).use { doc.save(it) }
            }

            // Second pass: re-open/save to drop orphaned streams
            PDDocument.load(temp).use { doc ->
                FileOutputStream(output).use { doc.save(it) }
            }
            if (temp.exists() && temp.absolutePath != output.absolutePath) temp.delete()

            val newSize = output.length()
            Log.d(TAG, "PDF size ${originalSize} → $newSize bytes (${pct(originalSize, newSize)})")
            return newSize > 0
        } catch (e: Exception) {
            Log.w(TAG, "PDF optimize failed, keeping original", e)
            if (temp.exists()) temp.delete()
            return false
        }
    }

    /**
     * Downscale + JPEG-compress raw image bytes before embedding as base64 in HTML.
     * Keeps visual quality for screen/print while cutting HTML/PDF weight dramatically.
     */
    fun optimizeImageBytes(
        original: ByteArray,
        maxEdge: Int = 1280,
        quality: Int = 75
    ): OptimizedImage {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return OptimizedImage(original, sniffMime(original))
            }
            var sample = 1
            while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(original, 0, original.size, opts)
                ?: return OptimizedImage(original, sniffMime(original))

            val maxDim = maxOf(bmp.width, bmp.height)
            val scaled = if (maxDim > maxEdge) {
                val s = maxEdge.toFloat() / maxDim
                val tw = (bmp.width * s).toInt().coerceAtLeast(1)
                val th = (bmp.height * s).toInt().coerceAtLeast(1)
                val out = Bitmap.createScaledBitmap(bmp, tw, th, true)
                if (out != bmp) bmp.recycle()
                out
            } else bmp

            val hasAlpha = scaled.hasAlpha()
            val baos = ByteArrayOutputStream()
            if (hasAlpha) {
                // PNG for transparency (logos); still size-limited by maxEdge
                scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
                scaled.recycle()
                val bytes = baos.toByteArray()
                // If PNG is huge, fall back to JPEG on white background
                if (bytes.size > original.size && original.size > 50_000) {
                    OptimizedImage(original, sniffMime(original))
                } else {
                    OptimizedImage(bytes, "image/png")
                }
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 90), baos)
                scaled.recycle()
                val bytes = baos.toByteArray()
                if (bytes.size < original.size || original.size > 200_000) {
                    OptimizedImage(bytes, "image/jpeg")
                } else {
                    OptimizedImage(original, sniffMime(original))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "image optimize failed", e)
            OptimizedImage(original, sniffMime(original))
        }
    }

    data class OptimizedImage(val bytes: ByteArray, val mime: String)

    private fun sniffMime(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "image/png"
        if (bytes.size >= 4 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) return "image/gif"
        if (bytes.size >= 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()) return "image/webp"
        return "image/jpeg"
    }

    private fun pct(old: Long, new: Long): String {
        if (old <= 0) return "n/a"
        val p = ((new.toDouble() / old.toDouble()) * 100.0)
        return String.format("%.0f%%", p)
    }
}
