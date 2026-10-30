package com.dzirent.app.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.dzirent.app.util.ClipInput
import com.dzirent.app.util.ItemLabel
import com.dzirent.app.util.ItemLabels
import com.dzirent.app.util.PhotoScores
import com.dzirent.app.util.normalized
import com.dzirent.app.util.scoreLabels
import com.dzirent.app.util.zeroShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** A smaller set in label_embeddings.json (general check, patterns, fabrics), scored with its own softmax. */
@Serializable
private data class EmbeddingSet(val labels: List<String>, val embeddings: List<List<Float>>)

/** assets/ml/label_embeddings.json, made by tools/ml/make_text_embeddings.py. */
@Serializable
private data class LabelEmbeddingsFile(
    val labels: List<String>,
    val dim: Int,
    val embeddings: List<List<Float>>,
    val general: EmbeddingSet? = null,
    val patterns: EmbeddingSet? = null,
    val fabrics: EmbeddingSet? = null
)

/**
 * Recognizes the item in a photo with TinyCLIP's image encoder (MIT), run on the phone by
 * LiteRT, compared with label text embeddings made offline (tools/ml). The model loads lazily,
 * the first time a photo is added, and once per app run; a lock keeps the interpreter (not
 * thread-safe) to one photo at a time. Photos never leave the phone. If the model can't load,
 * [embed] returns null and the editor simply shows no suggestions.
 */
class ItemRecognizer private constructor(private val appContext: Context) {
    private val lock = Mutex()
    private var model: Interpreter? = null
    private var labels: List<ItemLabel> = emptyList()
    private var text: List<FloatArray> = emptyList()
    private var general: List<FloatArray> = emptyList()
    private var patterns: List<FloatArray> = emptyList()
    private var fabrics: List<FloatArray> = emptyList()
    private var failed = false

    /** The photo's normalized image embedding, or null if the model isn't available. */
    suspend fun embed(photo: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        lock.withLock {
            val interpreter = loadLocked() ?: return@withLock null
            val shape = interpreter.getInputTensor(0).shape() // [1, 224, 224, 3] or [1, 3, 224, 224]
            val channelsFirst = shape.size == 4 && shape[1] == 3
            val small = modelInput(photo)
            val input = ClipInput.toFloats(small.toRgbImage(), channelsFirst)
            if (small !== photo) small.recycle()
            val buffer = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
            buffer.asFloatBuffer().put(input)
            val output = Array(1) { FloatArray(interpreter.getOutputTensor(0).shape().last()) }
            interpreter.run(buffer, output)
            normalized(output[0])
        }
    }

    /**
     * Every label's probability for an embedding from [embed] (best first), the general check's
     * "not fashion" probability, and the pattern and fabric probabilities. Null if the model
     * isn't loaded.
     */
    suspend fun scores(embedding: FloatArray): PhotoScores? = lock.withLock {
        if (text.isEmpty() || text.first().size != embedding.size) return@withLock null
        PhotoScores(
            labels = scoreLabels(embedding, text, labels),
            notFashion = if (general.size == ItemLabels.GENERAL.size) zeroShot(embedding, general)[1] else 0.0,
            patterns = if (patterns.size == ItemLabels.PATTERNS.size) zeroShot(embedding, patterns) else DoubleArray(0),
            fabrics = if (fabrics.size == ItemLabels.FABRICS.size) zeroShot(embedding, fabrics) else DoubleArray(0)
        )
    }

    private fun loadLocked(): Interpreter? {
        model?.let { return it }
        if (failed) return null
        return try {
            val file = Json { ignoreUnknownKeys = true }.decodeFromString<LabelEmbeddingsFile>(
                appContext.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
            )
            // Only labels the app knows, in the file's order (make_text_embeddings.py writes ItemLabels' order).
            val known = file.labels.withIndex().mapNotNull { (i, name) -> ItemLabels.byName(name)?.let { it to i } }
            labels = known.map { it.first }
            text = known.map { (_, i) -> normalized(file.embeddings[i].toFloatArray()) }
            // Each set only when its names are the ones the app expects, in the same order.
            fun set(found: EmbeddingSet?, expected: List<String>) =
                found?.takeIf { it.labels == expected }?.embeddings?.map { normalized(it.toFloatArray()) }.orEmpty()
            general = set(file.general, ItemLabels.GENERAL)
            patterns = set(file.patterns, ItemLabels.PATTERNS.map { it.key })
            fabrics = set(file.fabrics, ItemLabels.FABRICS)
            val mapped = appContext.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
            Interpreter(mapped, Interpreter.Options().setNumThreads(4)).also { model = it }
        } catch (e: Throwable) {
            Log.w(TAG, "Item recognition unavailable", e)
            failed = true
            null
        }
    }

    companion object {
        private const val TAG = "ItemRecognizer"
        const val MODEL_ASSET = "ml/tinyclip_image.tflite"
        const val LABELS_ASSET = "ml/label_embeddings.json"

        @Volatile
        private var instance: ItemRecognizer? = null

        fun get(context: Context): ItemRecognizer =
            instance ?: synchronized(this) { instance ?: ItemRecognizer(context.applicationContext).also { instance = it } }

        /**
         * The centered square of the photo at 224 x 224. Halving first (each step averages
         * 2 x 2 pixels) keeps fine patterns from aliasing the way one big bilinear step would.
         */
        fun modelInput(photo: Bitmap): Bitmap {
            val (x, y, side) = ClipInput.centerSquare(photo.width, photo.height)
            var square = Bitmap.createBitmap(photo, x, y, side, side)
            while (square.width >= ClipInput.SIZE * 2) {
                val half = Bitmap.createScaledBitmap(square, square.width / 2, square.height / 2, true)
                if (square !== photo) square.recycle()
                square = half
            }
            val result = Bitmap.createScaledBitmap(square, ClipInput.SIZE, ClipInput.SIZE, true)
            if (square !== photo && square !== result) square.recycle()
            return result
        }
    }
}
