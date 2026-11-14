package com.liquidvolume.module

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignStudioTest {
    @Test fun studioOpensAndPreviewSupportsFirstStepAndMute() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(activity.get().window.decorView)
        val preview = LiquidGlassPreviewView(RuntimeEnvironment.getApplication())
        preview.layout(0, 0, 700, 950)
        fun render(step: Int): Bitmap {
            preview.setPreviewStep(step)
            return Bitmap.createBitmap(700, 950, Bitmap.Config.ARGB_8888).also {
                preview.draw(Canvas(it))
            }
        }
        val muted = render(0)
        val first = render(1)
        assertFalse(muted.sameAs(first))
        assertFalse(first.sameAs(render(2)))
        assertFalse(render(50).sameAs(render(49)))
        assertTrue(muted.sameAs(render(0)))
        assertEquals(0, preview.previewStep)
        preview.setPreviewStep(999)
        assertEquals(150, preview.previewStep)
        activity.pause().stop().destroy()
    }
}
