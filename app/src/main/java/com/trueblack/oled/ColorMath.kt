package com.trueblack.oled

import java.util.Locale

/**
 * Builds the 4x4 colour matrix SurfaceFlinger applies to every pixel at composition time.
 *
 * Model (identical to the Windows DWM version):
 *   C' = max(0, (sat(C) - T) / (1 - T))
 * Greys at or below the threshold T land exactly on 0.0 (#000000, pixel off).
 * Everything above is re-stretched so white stays white, with an optional saturation gain.
 *
 * SurfaceFlinger reads the matrix column-major: index = column * 4 + row.
 * The translation lives in column 3; the last row must stay (0, 0, 0, 1).
 */
object ColorMath {
    const val MIN_THRESHOLD = 0.01f
    const val MAX_THRESHOLD = 0.30f
    const val MIN_SATURATION = 0.80f
    const val MAX_SATURATION = 1.60f

    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    fun matrix(threshold: Float, saturation: Float): FloatArray {
        val t = threshold.coerceIn(0f, 0.35f)
        val s = saturation.coerceIn(0.5f, 2.0f)
        val k = 1f / (1f - t)
        val lr = LR * (1f - s)
        val lg = LG * (1f - s)
        val lb = LB * (1f - s)
        val m = FloatArray(16)
        // column 0 — contribution of input R
        m[0] = k * (lr + s); m[1] = k * lr; m[2] = k * lr; m[3] = 0f
        // column 1 — contribution of input G
        m[4] = k * lg; m[5] = k * (lg + s); m[6] = k * lg; m[7] = 0f
        // column 2 — contribution of input B
        m[8] = k * lb; m[9] = k * lb; m[10] = k * (lb + s); m[11] = 0f
        // column 3 — translation (the black floor)
        m[12] = -k * t; m[13] = -k * t; m[14] = -k * t; m[15] = 1f
        return m
    }

    fun identity(): FloatArray = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    /** `service call SurfaceFlinger 1015 ...` arguments for the shell fallback. */
    fun serviceCallArgs(m: FloatArray?): List<String> {
        val args = mutableListOf("service", "call", "SurfaceFlinger", "1015", "i32")
        if (m == null) {
            args += "0"
        } else {
            args += "1"
            for (v in m) { args += "f"; args += String.format(Locale.US, "%.6f", v) }
        }
        return args
    }

    fun percent(threshold: Float): Int = Math.round(threshold * 100f)
}
