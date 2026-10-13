package com.aeswox.arcmusic.ui.haptics

/**
 * Intensity scale for haptic feedback throughout the app.
 */
enum class HapticIntensity(
    val label: String,
    val level: Int,
    val scaleFactor: Float
) {
    LIGHT("Light", 0, 0.45f),
    MEDIUM("Medium", 1, 0.80f),
    HEAVY("Heavy", 2, 1.0f);

    companion object {
        fun fromLevel(level: Int): HapticIntensity =
            entries.find { it.level == level } ?: MEDIUM

        fun fromString(str: String?): HapticIntensity =
            entries.find { it.name.equals(str, ignoreCase = true) } ?: MEDIUM
    }
}
