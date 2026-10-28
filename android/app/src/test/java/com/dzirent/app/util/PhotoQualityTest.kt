package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhotoQualityTest {

    private val w = 320
    private val h = 240

    private fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    private fun gray(v: Int) = rgb(v, v, v)

    private fun image(f: (x: Int, y: Int) -> Int) = RgbImage(w, h, IntArray(w * h) { f(it % w, it / w) })

    /** 32 px squares, [lo] and [hi] gray: lots of hard edges. */
    private fun checkerboard(lo: Int = 60, hi: Int = 200) = image { x, y -> gray(if ((x / 32 + y / 32) % 2 == 0) lo else hi) }

    /** A box blur of [radius] px, the way an out-of-focus or shaken photo smears edges. */
    private fun blurred(src: RgbImage, radius: Int): RgbImage {
        val g = src.gray()
        fun at(x: Int, y: Int) = g[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        val rows = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            (-radius..radius).sumOf { at(x + it, y) } / (2 * radius + 1)
        }
        return image { x, y -> gray((-radius..radius).sumOf { rows[(y + it).coerceIn(0, h - 1) * w + x] } / (2 * radius + 1)) }
    }

    // ---- Sharp ---------------------------------------------------------------------------

    @Test
    fun sharpEdgesPass() {
        val result = checkSharpness(checkerboard().gray(), w, h)
        assertEquals(CheckStatus.PASS, result.status)
        assertNull(result.issue)
    }

    @Test
    fun blurredEdgesFailAndBlock() {
        val result = checkSharpness(blurred(checkerboard(), radius = 10).gray(), w, h)
        assertEquals(CheckStatus.FAIL, result.status)
        assertEquals(PhotoIssue.BLURRY, result.issue)
        assertEquals("Too blurry: hold steady and retake", result.issue!!.message)
        assertEquals(Severity.BLOCK, result.issue!!.severity)
    }

    @Test
    fun aPlainPhotoCantBeJudgedForSharpness() {
        val result = checkSharpness(image { _, _ -> gray(128) }.gray(), w, h)
        assertEquals(CheckStatus.UNSURE, result.status)
        assertEquals("insufficient_detail", result.note)
    }

    @Test
    fun laplacianMatchesOpenCvOnASingleDot() {
        val g = IntArray(9).also { it[4] = 10 } // 3 x 3, center 10
        val lap = laplacian(g, 3, 3)
        assertEquals(-40.0, lap[4], 0.0)
        // Reflect-101 border: the corner's neighbours left and up mirror to (1, 0) and (0, 1).
        assertEquals(0.0, lap[0], 0.0)
        assertEquals(20.0, lap[1], 0.0) // the dot below counts twice (mirrored)
    }

    @Test
    fun percentileInterpolatesLikeNumpy() {
        val values = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(3.7, percentile(values, 90.0), 1e-9)
        assertEquals(2.5, percentile(values, 50.0), 1e-9)
    }

    // ---- Lighting ------------------------------------------------------------------------

    private fun textured(base: Int, spread: Int = 40, seed: Int = 1): RgbImage {
        val random = Random(seed)
        return image { _, _ -> gray(base + random.nextInt(-spread, spread + 1)) }
    }

    @Test
    fun evenLightPasses() {
        val result = checkLighting(textured(120).gray(), w, h)
        assertEquals(CheckStatus.PASS, result.status)
    }

    @Test
    fun darkPhotoFailsAndBlocks() {
        val result = checkLighting(textured(25, spread = 15).gray(), w, h)
        assertEquals(PhotoIssue.TOO_DARK, result.issue)
        assertEquals("Too dark: try daylight", result.issue!!.message)
        assertEquals(Severity.BLOCK, result.issue!!.severity)
    }

    @Test
    fun brightPhotoOnlyWarns() {
        val result = checkLighting(textured(245, spread = 12).gray(), w, h)
        assertEquals(PhotoIssue.TOO_BRIGHT, result.issue)
        assertEquals(Severity.WARN, result.issue!!.severity)
    }

    @Test
    fun aBlownOutSpotOnADarkerItemIsGlare() {
        val img = image { x, y ->
            // A navy item with a flash reflection in the middle.
            if (x in 140..180 && y in 100..140) gray(255) else rgb(40 + (x + y) % 40, 60, 120)
        }
        val result = checkLighting(img.gray(), w, h)
        assertEquals(PhotoIssue.GLARE, result.issue)
        assertEquals(Severity.WARN, result.issue!!.severity)
    }

    @Test
    fun aWhiteItemIsNotGlare() {
        val img = image { x, y -> if (x in 80..240 && y in 60..180) gray(252) else gray(110 + (x * y) % 30) }
        val result = checkLighting(img.gray(), w, h)
        assertTrue(result.issue != PhotoIssue.GLARE)
    }

    @Test
    fun blurIsNotJudgedWhenThePhotoIsTooDark() {
        val dark = blurred(checkerboard(lo = 0, hi = 30), radius = 10)
        val checks = checkPhoto(dark)
        assertEquals(PhotoIssue.TOO_DARK, checks.lighting.issue)
        assertEquals(CheckStatus.UNSURE, checks.sharp.status)
        assertEquals(listOf(PhotoIssue.TOO_DARK), checks.issues)
    }

    // ---- Whole item visible ----------------------------------------------------------------

    private val beige = rgb(222, 208, 186)
    private val maroon = rgb(128, 0, 32)

    /** A maroon "garment" on a plain beige surface, with a little sensor noise. */
    private fun garment(x0: Int, y0: Int, x1: Int, y1: Int, seed: Int = 7): RgbImage {
        val random = Random(seed)
        return image { x, y ->
            val n = random.nextInt(-3, 4)
            val base = if (x in x0 until x1 && y in y0 until y1) maroon else beige
            rgb((base shr 16 and 0xFF) + n, (base shr 8 and 0xFF) + n, (base and 0xFF) + n)
        }
    }

    @Test
    fun centeredItemIsWhollyVisible() {
        val result = checkWholeItem(garment(90, 50, 230, 190))
        assertEquals(CheckStatus.PASS, result.status)
        assertNull(result.issue)
    }

    @Test
    fun itemTouchingTheTopIsCutOff() {
        val result = checkWholeItem(garment(90, 0, 230, 160))
        assertEquals(PhotoIssue.CUT_OFF_TOP, result.issue)
        assertEquals("Top of the item is cut off", result.issue!!.message)
        assertEquals(Severity.WARN, result.issue!!.severity)
    }

    @Test
    fun itemTouchingEachOtherEdgeNamesThatEdge() {
        assertEquals(PhotoIssue.CUT_OFF_BOTTOM, checkWholeItem(garment(90, 80, 230, h)).issue)
        assertEquals(PhotoIssue.CUT_OFF_LEFT, checkWholeItem(garment(0, 50, 150, 190)).issue)
        assertEquals(PhotoIssue.CUT_OFF_RIGHT, checkWholeItem(garment(170, 50, w, 190)).issue)
    }

    @Test
    fun busyBackgroundIsUnsureNotAFailure() {
        val random = Random(3)
        val busy = image { _, _ -> rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        val result = checkWholeItem(busy)
        assertEquals(CheckStatus.UNSURE, result.status)
        assertEquals("background_not_uniform", result.note)
    }

    @Test
    fun noItemIsUnsure() {
        val result = checkWholeItem(garment(0, 0, 0, 0))
        assertEquals(CheckStatus.UNSURE, result.status)
        assertEquals("garment_not_detected", result.note)
    }

    @Test
    fun morphologyOpenDropsSpecksAndKeepsBlocks() {
        val mask = BooleanArray(20 * 20) { i -> val x = i % 20; val y = i / 20; (x in 5..14 && y in 5..14) || (x == 1 && y == 1) }
        val opened = dilate(erode(mask, 20, 20, 3), 20, 20, 3)
        assertTrue(!opened[1 * 20 + 1])
        assertTrue(opened[10 * 20 + 10])
        assertEquals(100, opened.count { it })
    }

    @Test
    fun largestRegionIsEightConnected() {
        val mask = BooleanArray(5 * 5)
        listOf(0, 6, 12, 24).forEach { mask[it] = true } // a diagonal of 3 plus a lone corner
        assertEquals(3, largestRegion(mask, 5, 5)!!.size)
    }

    @Test
    fun labMatchesKnownValues() {
        val white = rgbToLab(255, 255, 255)
        assertEquals(100.0, white[0], 0.01)
        assertEquals(0.0, white[1], 0.01)
        val red = rgbToLab(255, 0, 0)
        assertEquals(53.24, red[0], 0.05)
        assertEquals(80.09, red[1], 0.1)
        assertEquals(67.20, red[2], 0.1)
    }
}
