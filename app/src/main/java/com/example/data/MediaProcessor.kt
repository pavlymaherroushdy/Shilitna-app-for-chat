package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ProcessedImage(
  val base64: String,
  val fileName: String
)

data class ProcessedVideo(
  val thumbnailBase64: String?,
  val durationMs: Long,
  val fileName: String,
  val uriString: String
)

object MediaProcessor {

  suspend fun processPickedImage(context: Context, uri: Uri): Result<ProcessedImage> =
    withContext(Dispatchers.IO) {
      runCatching {
        val fileName = resolveFileName(context, uri, fallback = "photo.jpg").take(120)
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("تعذر قراءة الصورة المختارة")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val maxDim = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sampleSize = 1
        while (maxDim / sampleSize > 1280) {
          sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
          ?: error("صيغة الصورة غير مدعومة")

        val scaled = scaleBitmap(decoded, maxEdge = 640)
        var quality = 65
        var encoded: String
        do {
          val baos = ByteArrayOutputStream()
          scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
          encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
          quality -= 15
        } while (encoded.length > 320_000 && quality >= 20)

        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        require(encoded.length <= 340_000) { "حجم الصورة كبير جداً، اختر صورة أصغر" }
        ProcessedImage(base64 = encoded, fileName = fileName)
      }
    }

  suspend fun processPickedVideo(context: Context, uri: Uri): Result<ProcessedVideo> =
    withContext(Dispatchers.IO) {
      runCatching {
        val fileName = resolveFileName(context, uri, fallback = "video.mp4").take(120)
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var thumbBase64: String? = null
        try {
          retriever.setDataSource(context, uri)
          durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceIn(0L, 36_000_000L) ?: 0L
          val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          if (frame != null) {
            val scaled = scaleBitmap(frame, maxEdge = 480)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 55, baos)
            val rawBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            if (rawBase64.length <= 320_000) {
              thumbBase64 = rawBase64
            }
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
          }
        } finally {
          runCatching { retriever.release() }
        }
        ProcessedVideo(
          thumbnailBase64 = thumbBase64,
          durationMs = durationMs,
          fileName = fileName,
          uriString = uri.toString().take(500)
        )
      }
    }

  fun decodeBase64Image(base64: String?): ImageBitmap? {
    if (base64.isNullOrBlank()) return null
    return try {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
      null
    }
  }

  private fun scaleBitmap(source: Bitmap, maxEdge: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (w <= maxEdge && h <= maxEdge) return source
    val scale = maxEdge.toFloat() / max(w, h).toFloat()
    val targetW = (w * scale).roundToInt().coerceAtLeast(1)
    val targetH = (h * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetW, targetH, true)
  }

  private fun resolveFileName(context: Context, uri: Uri, fallback: String): String {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
          } else null
        } ?: fallback
    } catch (_: Exception) {
      fallback
    }
  }
}
