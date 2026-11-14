package com.liquidvolume.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PrecisionDragTest {
    private val length = 591f

    // The actual Samsung trackTouchEvent equation from the supplied SystemUI APK.
    private fun nativeResult(delta: Float, down: Float, progress: Int,
        min: Int = 0, max: Int = 1500): Int {
        val mapped = PrecisionDrag.remap(delta, down, length, progress, min, max)
        return (progress + mapped / length * (max - min)).roundToInt().coerceIn(min, max)
    }

    @Test fun noJumpOnTouchAndReversal() {
        for (progress in listOf(0, 10, 100, 500, 1490, 1500)) {
            for (down in listOf(25f, 100f, 300f, 550f)) {
                assertEquals(progress, nativeResult(0f, down, progress))
                assertEquals(progress, nativeResult(0.001f, down, progress))
                assertEquals(progress, nativeResult(-0.001f, down, progress))
            }
        }
    }

    @Test fun endpointsReachableFromEveryStartingLevelAndTouchPosition() {
        for (progress in listOf(0, 10, 100, 500, 1490, 1500)) {
            for (down in listOf(25f, 100f, 300f, 550f)) {
                assertEquals(0, nativeResult(-down, down, progress))
                assertEquals(0, nativeResult(-down - 500f, down, progress))
                assertEquals(1500, nativeResult(length - down, down, progress))
                assertEquals(1500, nativeResult(length - down + 500f, down, progress))
            }
        }
    }

    @Test fun monotonicAcrossEntireGestureAndNonZeroMinimum() {
        for ((min, max) in listOf(0 to 1500, 100 to 700)) {
            for (progress in listOf(min, (min + max) / 2, max)) {
                for (down in listOf(25f, 300f, 550f)) {
                    var previous = min
                    for (position in 0..591) {
                        val value = nativeResult(position - down, down, progress, min, max)
                        assertTrue(value in previous..max)
                        previous = value
                    }
                    assertEquals(max, previous)
                }
            }
        }
    }

    @Test fun lowEndHasMoreTravelPerNativeMediaStep() {
        // Same gesture: a ten-pixel move near zero changes less than one near mid-level.
        val low = nativeResult(-220f, 300f, 500) - nativeResult(-230f, 300f, 500)
        val high = nativeResult(-20f, 300f, 500) - nativeResult(-30f, 300f, 500)
        assertTrue(low < high)
        // Samsung floors media progress to multiples of 10. The first positive
        // native step must be selectable, not merely a theoretical float value.
        val steps = (0..300).map { nativeResult(it - 300f, 300f, 500) / 10 * 10 }
        assertTrue(steps.containsAll(listOf(0, 10, 20, 30, 40, 50)))
    }

    @Test fun unsupportedGeometryFallsBackToOriginalDelta() {
        for (down in listOf(-1f, 0f, length, length + 1f, Float.NaN)) {
            assertEquals(-17f, PrecisionDrag.remap(-17f, down, length, 500, 0, 1500), 0f)
        }
        assertEquals(-17f, PrecisionDrag.remap(-17f, 100f, 0f, 500, 0, 1500), 0f)
        assertEquals(-17f, PrecisionDrag.remap(-17f, 100f, length, 0, 0, 0), 0f)
    }
}
