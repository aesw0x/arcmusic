package com.aeswox.arcmusic.ui.haptics

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Global Haptic Feedback Engine for Arc Music.
 *
 * Utilizes Android 12+ hardware vibration primitives (`VibrationEffect.Composition`)
 * with nuanced amplitude scaling and dependable waveform fallbacks.
 */
object AppHaptics {

    @Volatile
    var isEnabled: Boolean = true

    @Volatile
    var intensity: HapticIntensity = HapticIntensity.MEDIUM

    private var vibrator: Vibrator? = null

    /**
     * Initializes the haptics engine with the application context.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Standard crisp click for controls, primary buttons, bottom nav items.
     */
    fun click() {
        if (!isEnabled) return
        performClick(intensity.scaleFactor)
    }

    /**
     * Subtle micro-tick for scrubbing seekbars, fine sliders, passing drag slots.
     */
    fun tick() {
        if (!isEnabled) return
        performTick(intensity.scaleFactor)
    }

    /**
     * Heavy, prominent thud for destructive and irreversible actions
     * (deleting songs, deleting playlists, clearing queue, clearing caches/resets).
     */
    fun destructive() {
        if (!isEnabled) return
        performDestructive(intensity.scaleFactor)
    }

    /**
     * Tactile pulse for favoriting/liking content.
     * When favoriting, delivers a playful rise-and-pop double pulse.
     * When un-favoriting, delivers a softer click.
     */
    fun favorite(isFavorited: Boolean = true) {
        if (!isEnabled) return
        if (isFavorited) {
            performFavorite(intensity.scaleFactor)
        } else {
            performClick(intensity.scaleFactor * 0.75f)
        }
    }

    /**
     * Sensation when lifting an item to drag or triggering a long-press selection.
     */
    fun grab() {
        if (!isEnabled) return
        performGrab(intensity.scaleFactor)
    }

    /**
     * Sensation when releasing or dropping an item into its new slot.
     */
    fun drop() {
        if (!isEnabled) return
        performDrop(intensity.scaleFactor)
    }

    /**
     * Rejection or error pattern (e.g. transfer failed, invalid action).
     */
    fun reject() {
        if (!isEnabled) return
        performReject(intensity.scaleFactor)
    }

    /**
     * Toggle feedback for switch changes.
     */
    fun toggle(checked: Boolean) {
        if (!isEnabled) return
        if (checked) click() else tick()
    }

    /**
     * Preview trigger used by Developer Settings to demonstrate tactile feel.
     */
    fun preview(targetIntensity: HapticIntensity) {
        performClick(targetIntensity.scaleFactor)
    }

    /**
     * Preview destructive thud specifically.
     */
    fun previewDestructive(targetIntensity: HapticIntensity) {
        performDestructive(targetIntensity.scaleFactor)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Implementation Details (API 31+ Primitives with Waveform Fallbacks)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun performClick(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale.coerceIn(0.1f, 1f))
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                val amp = (170 * scale).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 15), intArrayOf(0, amp), -1)
                vib.vibrate(effect)
            }
        } catch (_: Exception) { }
    }

    private fun performTick(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, (scale * 0.7f).coerceIn(0.1f, 1f))
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                val amp = (90 * scale).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 8), intArrayOf(0, amp), -1)
                vib.vibrate(effect)
            }
        } catch (_: Exception) { }
    }

    private fun performDestructive(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_THUD
                )
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, (scale * 0.6f).coerceIn(0.1f, 1f))
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale.coerceIn(0.1f, 1f), 20)
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale.coerceIn(0.1f, 1f))
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                // Dual impact heavy waveform
                val ampPrimary = (255 * scale).toInt().coerceIn(1, 255)
                val ampSecondary = (210 * scale).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 25, 55),
                    intArrayOf(0, ampPrimary, 0, ampSecondary),
                    -1
                )
                vib.vibrate(effect)
            }
        } catch (_: Exception) { }
    }

    private fun performFavorite(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                )
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, (scale * 0.5f).coerceIn(0.1f, 1f))
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale.coerceIn(0.1f, 1f), 15)
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                val amp1 = (150 * scale).toInt().coerceIn(1, 255)
                val amp2 = (240 * scale).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 16, 45, 24),
                    intArrayOf(0, amp1, 0, amp2),
                    -1
                )
                vib.vibrate(effect)
            }
        } catch (_: Exception) { }
    }

    private fun performGrab(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale.coerceIn(0.1f, 1f))
                    .compose()
                vib.vibrate(effect)
            } else {
                val amp = (200 * scale).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 26), intArrayOf(0, amp), -1)
                vib.vibrate(effect)
            }
        } catch (_: Exception) { }
    }

    private fun performDrop(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale.coerceIn(0.1f, 1f))
                    .compose()
                vib.vibrate(effect)
            } else {
                performClick(scale * 0.85f)
            }
        } catch (_: Exception) { }
    }

    private fun performReject(scale: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            val amp1 = (220 * scale).toInt().coerceIn(1, 255)
            val amp2 = (180 * scale).toInt().coerceIn(1, 255)
            val amp3 = (130 * scale).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 30, 35, 30, 35, 25),
                intArrayOf(0, amp1, 0, amp2, 0, amp3),
                -1
            )
            vib.vibrate(effect)
        } catch (_: Exception) { }
    }
}
