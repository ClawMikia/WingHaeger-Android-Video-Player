package com.winghaeger.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ThumbnailExtractor {
    private const val MAX_W = 320
    private const val MAX_H = 180
    private const val JPEG_QUALITY = 82

    fun extractJpeg(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let { scaleToJpeg(it) }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) { }
        }
    }

    private fun scaleToJpeg(src: Bitmap): ByteArray {
        val w = src.width
        val h = src.height
        val scale = min(MAX_W.toFloat() / w, MAX_H.toFloat() / h).coerceAtMost(1f)
        val tw = (w * scale).toInt().coerceAtLeast(1)
        val th = (h * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(src, tw, th, true) else src
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== src) scaled.recycle()
        if (src !== scaled) src.recycle()
        return out.toByteArray()
    }
}
