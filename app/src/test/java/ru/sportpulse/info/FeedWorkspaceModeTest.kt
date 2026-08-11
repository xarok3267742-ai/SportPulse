package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedWorkspaceModeTest {
    @Test
    fun focusIsTheDefaultForNewUsers() {
        assertEquals(
            FeedWorkspaceMode.FOCUS,
            FeedWorkspaceMode.fromStored(null)
        )
    }

    @Test
    fun storedToolsModeIsRestored() {
        assertEquals(
            FeedWorkspaceMode.TOOLS,
            FeedWorkspaceMode.fromStored("TOOLS")
        )
    }

    @Test
    fun unknownStoredValueFallsBackToFocus() {
        assertEquals(
            FeedWorkspaceMode.FOCUS,
            FeedWorkspaceMode.fromStored("REMOVED_MODE")
        )
    }
}
