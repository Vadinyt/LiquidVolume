package com.liquidvolume.module

import kotlin.math.sqrt

/** Maps a relative gesture to the available travel, with finer control near zero. */
internal object PrecisionDrag {
    fun remap(delta: Float, touchDown: Float, length: Float,
        progressAtDown: Int, min: Int, max: Int): Float {
        if (!delta.isFinite() || !touchDown.isFinite() || !length.isFinite() ||
            length <= 0f || max <= min || progressAtDown !in min..max ||
            touchDown <= 0f || touchDown >= length || delta == 0f) return delta

        val range = max.toDouble() - min
        val start = sqrt((progressAtDown.toDouble() - min) / range)
        val position = (touchDown + delta).coerceIn(0f, length)
        // Anchor at the existing level: touching the slider never jumps the sound.
        // Both physical ends remain reachable irrespective of the initial touch.
        val precise = if (delta < 0f) {
            start * (position / touchDown)
        } else {
            start + (1.0 - start) * ((position - touchDown) / (length - touchDown))
        }
        val target = min + range * precise * precise
        // Samsung still applies this delta, clamps, rounds and dispatches audio.
        return ((target - progressAtDown) * length / range).toFloat()
    }
}
