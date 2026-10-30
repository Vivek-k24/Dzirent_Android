package com.dzirent.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.dzirent.app.util.ColorVotes
import com.dzirent.app.util.PhotoChecks
import com.dzirent.app.util.PhotoScores
import com.dzirent.app.util.PhotoThresholds
import com.dzirent.app.util.RgbImage
import com.dzirent.app.util.analyzeImage
import com.dzirent.app.util.applyExifOrientation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the phone worked out about one photo: the checks (null when it couldn't be read), the
 * image embedding for item recognition and the same-item check (null if the model isn't
 * available), the model's scores for it, and the color votes from the garment area.
 */
class PhotoAnalysis(
    val checks: PhotoChecks?,
    val embedding: FloatArray? = null,
    val scores: PhotoScores? = null,
    val colors: ColorVotes = ColorVotes.EMPTY
)

/**
 * Checks photos as they're added to a listing and runs item recognition on them. Everything
 * runs on the phone, off the main thread; the photo is only read, never sent anywhere.
 *
 * Camera and gallery photos take the same path: the file is decoded as stored, turned upright
 * from its EXIF orientation by [applyExifOrientation] (pure Kotlin, so every orientation is
 * handled the same on every Android version), then checked and embedded.
 */
class PhotoAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    private val recognizer = ItemRecognizer.get(appContext)

    suspend fun analyze(uri: Uri): PhotoAnalysis = withContext(Dispatchers.Default) {
        val image = guarded { decodeUpright(appContext, uri, PhotoThresholds.WORKING_LONG_SIDE) }
            ?: return@withContext PhotoAnalysis(checks = null)
        // A photo the checks can't handle (odd size, out of memory) is treated like one that
        // can't be read; recognition failing only means no suggestions.
        val analysis = guarded { analyzeImage(image) } ?: return@withContext PhotoAnalysis(checks = null)
        val embedding = guarded {
            val bitmap = image.toBitmap()
            try {
                recognizer.embed(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        val scores = embedding?.let { e -> guarded { recognizer.scores(e) } }
        PhotoAnalysis(checks = analysis.checks, embedding = embedding, scores = scores, colors = analysis.colors)
    }

    /** The model's scores for an embedding (several photos averaged), or null if the model isn't available. */
    suspend fun scores(embedding: FloatArray): PhotoScores? = guarded { recognizer.scores(embedding) }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Photo analysis step failed", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Photo analysis ran out of memory", e)
        null
    }

    companion object {
        private const val TAG = "PhotoAnalyzer"

        /**
         * Decodes [uri] scaled down so the long side is at most [maxSide], then turns it upright
         * from its EXIF orientation. Null if it can't be read.
         */
        fun decodeUpright(context: Context, uri: Uri, maxSide: Int): RgbImage? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            } ?: return null
            val scale = minOf(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
            val sized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    max(1, (decoded.width * scale).roundToInt()),
                    max(1, (decoded.height * scale).roundToInt()),
                    true
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
            val raw = sized.toRgbImage()
            sized.recycle()
            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            return applyExifOrientation(raw, orientation)
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

/** An opaque bitmap of the image, for the model. */
fun RgbImage.toBitmap(): Bitmap =
    Bitmap.createBitmap(IntArray(pixels.size) { pixels[it] or 0xFF000000.toInt() }, width, height, Bitmap.Config.ARGB_8888)
