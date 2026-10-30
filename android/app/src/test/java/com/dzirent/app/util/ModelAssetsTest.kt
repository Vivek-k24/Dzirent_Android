package com.dzirent.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shipped model files match the code (unit tests run from the app module folder). */
class ModelAssetsTest {

    private val dir = File("src/main/assets/ml")

    @Test
    fun labelEmbeddingsMatchTheLabelList() {
        val json = Json.parseToJsonElement(File(dir, "label_embeddings.json").readText()).jsonObject
        val labels = json.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
        assertEquals("tools/ml/labels.py and ItemLabels.kt must list the same labels in the same order", ItemLabels.NAMES, labels)

        val dim = json.getValue("dim").jsonPrimitive.int
        val embeddings = json.getValue("embeddings").jsonArray
        assertEquals(labels.size, embeddings.size)
        embeddings.forEach { row ->
            val v = row.jsonArray.map { it.jsonPrimitive.double }
            assertEquals(dim, v.size)
            assertEquals(1.0, kotlin.math.sqrt(v.sumOf { it * it }), 1e-3) // normalized
        }
        assertEquals(PhotoThresholds.LOGIT_SCALE, json.getValue("logit_scale").jsonPrimitive.double, 0.5)
    }

    @Test
    fun generalPatternAndFabricSetsMatchTheCode() {
        val json = Json.parseToJsonElement(File(dir, "label_embeddings.json").readText()).jsonObject
        val dim = json.getValue("dim").jsonPrimitive.int
        mapOf(
            "general" to ItemLabels.GENERAL,
            "patterns" to ItemLabels.PATTERNS.map { it.key },
            "fabrics" to ItemLabels.FABRICS
        ).forEach { (key, expected) ->
            val set = json.getValue(key).jsonObject
            assertEquals("$key in tools/ml/labels.py and ItemLabels.kt", expected, set.getValue("labels").jsonArray.map { it.jsonPrimitive.content })
            val rows = set.getValue("embeddings").jsonArray
            assertEquals(expected.size, rows.size)
            rows.forEach { assertEquals(dim, it.jsonArray.size) }
        }
    }

    @Test
    fun modelShipsAndStaysSmall() {
        val model = File(dir, "tinyclip_image.tflite")
        assertTrue("run tools/ml/export_image_encoder.py", model.isFile)
        assertTrue("model is ${model.length()} bytes", model.length() in 1_000_000L..25_000_000L)
    }
}
