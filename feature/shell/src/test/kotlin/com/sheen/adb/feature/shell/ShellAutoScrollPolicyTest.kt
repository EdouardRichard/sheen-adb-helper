package com.sheen.adb.feature.shell

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ShellAutoScrollPolicyTest {
    @Test
    fun `enabled auto scroll follows the last record for each new output generation`() {
        val previous = ShellScrollState(
            autoScroll = true,
            anchorIndex = 3,
            anchorOffset = 7,
            outputGeneration = 4,
        )

        val decision = ShellAutoScrollPolicy.onOutputChanged(
            previous = previous,
            itemCount = 9,
            outputGeneration = 5,
        )

        assertEquals(decision.targetIndex, 8)
        assertEquals(decision.state.anchorIndex, 8)
        assertEquals(decision.state.anchorOffset, 0)
        assertEquals(decision.state.outputGeneration, 5)
    }

    @Test
    fun `disabled auto scroll preserves the exact reading anchor on new output`() {
        val previous = ShellScrollState(
            autoScroll = false,
            anchorIndex = 3,
            anchorOffset = 19,
            outputGeneration = 4,
        )

        val decision = ShellAutoScrollPolicy.onOutputChanged(previous, itemCount = 9, outputGeneration = 5)

        assertNull(decision.targetIndex)
        assertEquals(decision.state.anchorIndex, 3)
        assertEquals(decision.state.anchorOffset, 19)
        assertEquals(decision.state.outputGeneration, 5)
    }

    @Test
    fun `enabling auto scroll explicitly jumps to the current end`() {
        val decision = ShellAutoScrollPolicy.setEnabled(
            previous = ShellScrollState(autoScroll = false, anchorIndex = 2, anchorOffset = 9),
            enabled = true,
            itemCount = 7,
        )

        assertTrue(decision.state.autoScroll)
        assertEquals(decision.targetIndex, 6)
        assertEquals(decision.state.anchorOffset, 0)
    }

    @Test
    fun `filter and clear clamp anchors safely without forcing disabled readers to scroll`() {
        val disabled = ShellScrollState(autoScroll = false, anchorIndex = 8, anchorOffset = 11)

        val filtered = ShellAutoScrollPolicy.onVisibleRecordsChanged(disabled, itemCount = 3)
        val cleared = ShellAutoScrollPolicy.onVisibleRecordsChanged(filtered.state, itemCount = 0)

        assertFalse(filtered.state.autoScroll)
        assertEquals(filtered.state.anchorIndex, 2)
        assertEquals(filtered.state.anchorOffset, 0)
        assertNull(filtered.targetIndex)
        assertEquals(cleared.state.anchorIndex, 0)
        assertEquals(cleared.state.anchorOffset, 0)
        assertNull(cleared.targetIndex)
    }
}
