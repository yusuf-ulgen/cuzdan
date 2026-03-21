package com.example.cuzdan.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Utility class to provide haptic feedback across the application.
 */
object HapticManager {

    /**
     * Triggers a standard click haptic effect on a view.
     */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /**
     * Triggers a long press haptic effect on a view.
     */
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Triggers a subtle tick effect (best for scroll/dial).
     */
    fun tick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Triggers a haptic effect for success.
     */
    fun success(context: Context) {
        vibrate(context, longArrayOf(0, 10, 50, 15), intArrayOf(0, 150, 0, 255))
    }

    /**
     * Triggers a haptic effect for error/warning.
     */
    fun error(context: Context) {
        vibrate(context, longArrayOf(0, 50, 50, 50), intArrayOf(0, 255, 0, 255))
    }

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}
