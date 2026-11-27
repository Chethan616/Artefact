package com.chethan616.clearpdf.ui

import com.chethan616.clearpdf.ui.viewer.QuickTool
import com.chethan616.clearpdf.ui.viewer.QuickToolbarPrefs
import com.chethan616.clearpdf.ui.viewer.toolbarSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToolbarTest {
    @Test
    fun slotsGrowWithTheScreen() {
        // as many as the bar on the page shows: five on a phone (a sixth came out too small)
        assertEquals(5, toolbarSlots(411))
        assertEquals(6, toolbarSlots(460))
        assertEquals(8, toolbarSlots(800))
        // the default bar fits a phone held upright
        assertTrue(QuickTool.DEFAULT_BAR.size <= toolbarSlots(360))
    }

    @Test
    fun theDockDropsOfficeToolsAndRepeats() {
        // an old layout may name a tool a PDF does not have, or name one twice
        assertEquals(
            listOf(QuickTool.Scan),
            QuickToolbarPrefs.validBar(listOf(QuickTool.Scan, QuickTool.Present, QuickTool.Scan)),
        )
        // nothing left: the dock's default tools
        assertEquals(QuickTool.DEFAULT_BAR, QuickToolbarPrefs.validBar(emptyList()))
        assertEquals(QuickTool.DEFAULT_BAR, QuickToolbarPrefs.validBar(listOf(QuickTool.Present)))
    }

    @Test
    fun idsAreStable() {
        assertEquals(QuickTool.entries.size, QuickTool.entries.map { it.id }.toSet().size)
        for (t in QuickTool.entries) assertEquals(t, QuickTool.of(t.id))
    }
}
