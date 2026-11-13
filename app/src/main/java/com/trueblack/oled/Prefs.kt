package com.trueblack.oled

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Preset(val threshold: Float, val saturation: Float) {
    STANDARD(0.12f, 1.05f),
    DEEP(0.16f, 1.10f),
    VIVID(0.14f, 1.25f),
    SUBTLE(0.08f, 1.00f);

    companion object {
        fun matching(threshold: Float, saturation: Float): Preset? =
            entries.firstOrNull {
                kotlin.math.abs(it.threshold - threshold) < 0.0005f &&
                    kotlin.math.abs(it.saturation - saturation) < 0.0005f
            }
    }
}

data class Settings(
    val enabled: Boolean = false,
    val threshold: Float = Preset.STANDARD.threshold,
    val saturation: Float = Preset.STANDARD.saturation,
    val restoreOnBoot: Boolean = true,
    val keepActive: Boolean = false,
    val forceDarkTheme: Boolean = false,
    val haptics: Boolean = true,
) {
    val preset: Preset? get() = Preset.matching(threshold, saturation)
}

/** Single source of truth for user settings; a StateFlow mirror feeds the UI. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("trueblack", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> get() = _flow
    val current: Settings get() = _flow.value

    private fun read() = Settings(
        enabled = sp.getBoolean(K_ENABLED, false),
        threshold = sp.getFloat(K_THRESHOLD, Preset.STANDARD.threshold),
        saturation = sp.getFloat(K_SATURATION, Preset.STANDARD.saturation),
        restoreOnBoot = sp.getBoolean(K_BOOT, true),
        keepActive = sp.getBoolean(K_KEEP, false),
        forceDarkTheme = sp.getBoolean(K_DARK, false),
        haptics = sp.getBoolean(K_HAPTICS, true),
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_flow.value)
        sp.edit()
            .putBoolean(K_ENABLED, s.enabled)
            .putFloat(K_THRESHOLD, s.threshold)
            .putFloat(K_SATURATION, s.saturation)
            .putBoolean(K_BOOT, s.restoreOnBoot)
            .putBoolean(K_KEEP, s.keepActive)
            .putBoolean(K_DARK, s.forceDarkTheme)
            .putBoolean(K_HAPTICS, s.haptics)
            .apply()
        _flow.value = s
    }

    /** Night-mode value captured before we forced dark theme ("yes", "no", "auto", or null). */
    var previousNightMode: String?
        get() = sp.getString(K_PREV_NIGHT, null)
        set(v) { sp.edit().putString(K_PREV_NIGHT, v).apply() }

    private companion object {
        const val K_ENABLED = "enabled"
        const val K_THRESHOLD = "threshold"
        const val K_SATURATION = "saturation"
        const val K_BOOT = "restore_on_boot"
        const val K_KEEP = "keep_active"
        const val K_DARK = "force_dark"
        const val K_HAPTICS = "haptics"
        const val K_PREV_NIGHT = "prev_night_mode"
    }
}
