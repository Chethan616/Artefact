package com.chethan616.clearpdf.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropStatsTest {
    private val side = 12

    private fun grey(v: Int): Int = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun sample(at: (x: Int, y: Int) -> Int) = IntArray(side * side) { at(it % side, it / side) }

    @Test
    fun anEvenWashIsCalm() {
        val s = backdropStats(sample { _, _ -> grey(240) }, side)!!
        assertEquals(240 / 255f, s.luminance, 0.01f)
        assertEquals(0f, s.busy, 0f)
    }

    @Test
    fun aSmoothGradientIsCalmThoughItSpansFromDarkToLight() {
        // the aurora: dark at one edge, light at the other, but no step between neighbours
        val s = backdropStats(sample { x, _ -> grey(60 + x * 15) }, side)!!
        assertEquals(0f, s.busy, 0.05f)
    }

    @Test
    fun linesOfPrintAreBusy() {
        // a white page with a dark line of type every other row
        val s = backdropStats(sample { _, y -> if (y % 2 == 0) grey(250) else grey(90) }, side)!!
        assertTrue("a page of print reads busy, was ${s.busy}", s.busy > 0.9f)
        assertTrue("and light", s.luminance > 0.6f)
    }

    @Test
    fun undrawnCellsSayNothing() {
        assertNull(backdropStats(IntArray(side * side), side))
        // half transparent, half an even grey: still calm, and the mean is the grey's alone
        val s = backdropStats(sample { x, _ -> if (x < 6) 0 else grey(128) }, side)!!
        assertEquals(128 / 255f, s.luminance, 0.01f)
        assertEquals(0f, s.busy, 0f)
    }

    @Test
    fun theEdgeIsOnlyForNearWhite() {
        assertEquals(androidx.compose.ui.graphics.Color.Unspecified, lightEdge(0.5f))
        assertEquals(androidx.compose.ui.graphics.Color.Unspecified, lightEdge(0.55f))
        assertTrue(lightEdge(0.7f).alpha in 0.03f..0.08f)
        assertTrue(lightEdge(0.98f).alpha > 0.1f)
    }

    @Test
    fun aBusyBackdropThickensTheWash() {
        assertTrue(adaptiveWash(0.95f, busy = 1f).alpha > adaptiveWash(0.95f).alpha + 0.3f)
        assertTrue(adaptiveWash(0.05f, busy = 1f).alpha > adaptiveWash(0.05f).alpha + 0.2f)
        assertEquals(adaptiveWash(0.7f).alpha, adaptiveWash(0.7f, busy = 0f).alpha, 0f)
    }
}
