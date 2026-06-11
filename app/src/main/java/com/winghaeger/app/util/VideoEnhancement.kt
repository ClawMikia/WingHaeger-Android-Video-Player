package com.winghaeger.app.util

import android.graphics.ColorMatrix
import com.winghaeger.app.data.EnhancementMode

/**
 * Client-side color-matrix presets for cinematic presentation. No re-encode.
 * Also provides a builder for arbitrary brightness/contrast/saturation/hue parameters.
 */
object VideoEnhancement {

    fun matrixFor(mode: EnhancementMode): ColorMatrix = when (mode) {
        EnhancementMode.NONE -> ColorMatrix()

        EnhancementMode.VIVID_HD -> buildMatrix(saturation = 1.35f, contrast = 1.12f)

        EnhancementMode.CINEMA_CONTRAST -> ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, -18f,
                0f, 1.2f, 0f, 0f, -18f,
                0f, 0f, 1.15f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        EnhancementMode.WARM_FILM -> buildMatrix(saturation = 1.08f).also {
            it.postConcat(ColorMatrix(floatArrayOf(
                1.08f, 0f, 0f, 0f, 8f,
                0f, 1.02f, 0f, 0f, 4f,
                0f, 0f, 0.95f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        EnhancementMode.COOL_HDR_SIM -> buildMatrix(saturation = 1.1f).also {
            it.postConcat(ColorMatrix(floatArrayOf(
                0.98f, 0f, 0f, 0f, 0f,
                0f, 1.02f, 0f, 0f, 6f,
                0f, 0f, 1.12f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        EnhancementMode.AMOLED -> ColorMatrix(floatArrayOf(
            1.20f, 0f, 0f, 0f, -30f,
            0f, 1.20f, 0f, 0f, -30f,
            0f, 0f, 1.20f, 0f, -30f,
            0f, 0f, 0f, 1f, 0f
        ))

        EnhancementMode.NIGHT_MODE -> ColorMatrix(floatArrayOf(
            0.80f, 0f, 0f, 0f, 0f,
            0f, 0.70f, 0f, 0f, 0f,
            0f, 0f, 0.60f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        EnhancementMode.ANIME -> buildMatrix(saturation = 1.5f, contrast = 1.08f).also {
            it.postConcat(ColorMatrix(floatArrayOf(
                1.05f, 0f, 0f, 0f, 5f,
                0f, 1.05f, 0f, 0f, 5f,
                0f, 0f, 1.10f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        EnhancementMode.EYE_COMFORT -> ColorMatrix(floatArrayOf(
            1.00f, 0f, 0f, 0f, 0f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 0.82f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        EnhancementMode.VIVID_OUTDOOR -> buildMatrix(saturation = 1.6f, contrast = 1.25f).also {
            it.postConcat(ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 10f,
                0f, 0f, 1.0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        EnhancementMode.CINEMATIC_DARK -> ColorMatrix(floatArrayOf(
            1.15f, 0.05f, 0f, 0f, -25f,
            0f, 1.10f, 0.05f, 0f, -20f,
            0.05f, 0f, 1.05f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    /**
     * Build a custom ColorMatrix from individual enhancement parameters.
     * All parameters are additive on top of each other.
     *
     * @param brightness  -1.0 to +1.0 (0 = no change)
     * @param contrast    0.5 to 2.0   (1 = no change)
     * @param saturation  0.0 to 2.0   (1 = no change)
     * @param hue         -180 to +180 degrees
     */
    fun buildCustomMatrix(
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        hue: Float = 0f,
    ): ColorMatrix {
        val result = ColorMatrix()

        // Saturation
        if (saturation != 1f) result.setSaturation(saturation)

        // Contrast + brightness combined
        val b = brightness * 255f
        val c = contrast
        val offset = b + (0.5f * (1f - c) * 255f)
        val contrastMatrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, offset,
            0f, c, 0f, 0f, offset,
            0f, 0f, c, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        result.postConcat(contrastMatrix)

        // Hue rotation (approximate using color rotation)
        if (hue != 0f) {
            val hueMatrix = ColorMatrix()
            hueMatrix.setRotate(0, hue) // rotate around red axis
            result.postConcat(hueMatrix)
        }

        return result
    }

    /**
     * Combine a preset matrix with custom slider overrides.
     * The preset is applied first, then the custom adjustments layered on top.
     */
    fun buildCombinedMatrix(
        mode: EnhancementMode,
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        hue: Float = 0f,
    ): ColorMatrix {
        val preset = matrixFor(mode)
        if (brightness == 0f && contrast == 1f && saturation == 1f && hue == 0f) return preset
        val custom = buildCustomMatrix(brightness, contrast, saturation, hue)
        preset.postConcat(custom)
        return preset
    }

    private fun buildMatrix(saturation: Float = 1f, contrast: Float = 1f): ColorMatrix {
        val m = ColorMatrix()
        if (saturation != 1f) m.setSaturation(saturation)
        if (contrast != 1f) {
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, (0.5f * (1f - contrast) * 255f),
                0f, contrast, 0f, 0f, (0.5f * (1f - contrast) * 255f),
                0f, 0f, contrast, 0f, (0.5f * (1f - contrast) * 255f),
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(cm)
        }
        return m
    }
}
