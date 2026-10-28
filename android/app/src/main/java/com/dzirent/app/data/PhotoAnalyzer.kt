package com.dzirent.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.dzirent.app.util.PhotoChecks
import com.dzirent.app.util.PhotoThresholds
import com.dzirent.app.util.RgbImage
import com.dzirent.app.util.checkPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The checks on one photo, or null checks when it couldn't be read. */
data class PhotoAnalysis(val checks: PhotoChecks?)

/**
 * Checks photos as they're added to a listing. Everything runs on the phone, off the main
 * thread; the photo is only read, never sent anywhere.
 */
class PhotoAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun analyze(uri: Uri): PhotoAnalysis = withContext(Dispatchers.Default) {
        val bitmap = decodeUpright(appContext, uri, PhotoThresholds.WORKING_LONG_SIDE)
            ?: return@withContext PhotoAnalysis(checks = null)
        try {
            PhotoAnalysis(checks = checkPhoto(bitmap.toRgbImage()))
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        /**
         * Decodes [uri] the right way up (EXIF orientation applied), scaled down so the long
         * side is at most [maxSide]. Null if it can't be read.
         */
        fun decodeUpright(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = maxSide.toFloat() / max(info.size.width, info.size.height)
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            max(1, (info.size.width * scale).roundToInt()),
                            max(1, (info.size.height * scale).roundToInt())
                        )
                    }
                }
                // HDR photos can decode as RGBA_F16; the checks read 8-bit pixels.
                if (decoded.config == Bitmap.Config.ARGB_8888) decoded
                else decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
            } else {
                decodeLegacy(context, uri, maxSide)
            }
        } catch (e: Exception) {
            null
        }

        private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scale = minOf(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
            if (rotation == 0f && scale == 1f) return decoded
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation)
            }
            return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        }
    }
}

/** The bitmap's pixels as 0xRRGGBB (alpha dropped) for the pure checks. */
fun Bitmap.toRgbImage(): RgbImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) pixels[i] = pixels[i] and 0xFFFFFF
    return RgbImage(width, height, pixels)
}
