package com.dzirent.app.util

/**
 * Every number the photo checks and the item recognition decide with, in one place.
 * Owner to tune: these started from the website's quality-rules-0.1.0 (services/ai-quality,
 * analysis.py), which were chosen against synthetic fixtures only, and from first guesses for
 * the model. Nothing here has been calibrated on real listing photos yet.
 */
object PhotoThresholds {
    // ---- Working size -------------------------------------------------------------------
    /** Photos are downscaled so the long side is at most this before checking (owner to tune). */
    const val WORKING_LONG_SIDE = 1280

    // ---- Sharp (blur) ---------------------------------------------------------------------
    /** The photo is split into this many tiles per side; only textured tiles are scored (owner to tune). */
    const val BLUR_TILES = 8
    /** A tile counts as textured when its gray standard deviation is above this (owner to tune). */
    const val BLUR_TEXTURE_STD = 8.0
    /** Fewer textured tiles than this: sharpness can't be judged (owner to tune). */
    const val BLUR_MIN_TILES = 4
    /** Which percentile of the tiles' Laplacian variance is the photo's score (owner to tune). */
    const val BLUR_PERCENTILE = 90.0
    /** Score at or above this is sharp (owner to tune). */
    const val SHARP_SCORE = 120.0
    /** Score below this is clearly blurry and blocks the photo (owner to tune). */
    const val BLURRY_SCORE = 35.0

    // ---- Lighting -------------------------------------------------------------------------
    /** Mean gray below this is too dark and blocks the photo (owner to tune). */
    const val DARK_MEAN = 45.0
    /** Gray at or below this is a "black" pixel (owner to tune). */
    const val DARK_PIXEL = 8
    /** More black pixels than this share is too dark (owner to tune). */
    const val DARK_SHARE = 0.40
    /** Mean gray above this is too bright: a warning (owner to tune). */
    const val BRIGHT_MEAN = 225.0
    /** Gray at or above this is a clipped (blown-out) pixel (owner to tune). */
    const val BRIGHT_PIXEL = 250
    /** More clipped pixels than this share is too bright (owner to tune). */
    const val BRIGHT_SHARE = 0.35
    /** Strong glare: clipped pixels make up at least this share of the center of the photo... (owner to tune) */
    const val GLARE_SHARE = 0.04
    /** ...while the center is mostly darker than this, so it's a highlight, not a white item (owner to tune). */
    const val GLARE_MAX_CENTER_MEDIAN = 170.0
    /** The "center" for glare and color: this share of the width and height, in the middle (owner to tune). */
    const val CENTER_SHARE = 0.5

    // ---- Whole item visible (framing) -----------------------------------------------------
    /** The background is sampled from a border this share of the short side wide (at least 4 px) (owner to tune). */
    const val BORDER_SHARE = 0.03
    /** A border pixel farther than this (Lab distance) from the median is "not background" (owner to tune). */
    const val BORDER_NOISE_DISTANCE = 15.0
    /** More of the border than this share not being background: framing can't be judged (owner to tune). */
    const val BORDER_MAX_NOISY_SHARE = 0.30
    /** Smallest Lab distance from the background that counts as the item (owner to tune). */
    const val ITEM_MIN_DISTANCE = 12.0
    /** The item must cover at least this share of the photo to be found (owner to tune). */
    const val ITEM_MIN_AREA = 0.03
    /** Outline pixels this close to the frame (px) touch the edge (owner to tune). */
    const val EDGE_MARGIN_PX = 2
    /** The item is cut off when at least this share of its outline touches the edge (owner to tune). */
    const val CUT_OFF_OUTLINE_SHARE = 0.02

    // ---- Item recognition (TinyCLIP) ------------------------------------------------------
    /** The logit scale: cosine similarities are multiplied by this before the softmax. TinyCLIP learned 50
     *  (OpenAI CLIP 100); make_text_embeddings.py writes it to label_embeddings.json and a test checks it. */
    const val LOGIT_SCALE = 50.0
    /** Title, category and description are suggested only at or above this top-label probability (owner to tune). */
    const val SUGGEST_CONFIDENCE = 0.45
    /** "Not allowed" / "not offered" warnings need at least this probability for the group (owner to tune). */
    const val POLICY_CONFIDENCE = 0.60
    /** Another photo whose image embedding is less similar than this to the cover's looks like a different item (owner to tune). */
    const val SAME_ITEM_MIN_SIMILARITY = 0.65
}
