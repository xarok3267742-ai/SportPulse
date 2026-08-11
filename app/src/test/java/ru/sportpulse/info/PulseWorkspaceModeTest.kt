package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Test

class PulseWorkspaceModeTest {
    @Test
    fun storyIsTheDefaultForNewUsers() {
        assertEquals(
            PulseWorkspaceMode.STORY,
            PulseWorkspaceMode.fromStored(null)
        )
    }

    @Test
    fun storedLabModeIsRestored() {
        assertEquals(
            PulseWorkspaceMode.LAB,
            PulseWorkspaceMode.fromStored("LAB")
        )
    }

    @Test
    fun unknownStoredValueFallsBackToStory() {
        assertEquals(
            PulseWorkspaceMode.STORY,
            PulseWorkspaceMode.fromStored("REMOVED_MODE")
        )
    }

    @Test
    fun onlyLaboratoryTracksActiveAnalysisTime() {
        assertEquals(
            false,
            PulseWorkspaceMode.STORY.tracksAttention
        )
        assertEquals(
            true,
            PulseWorkspaceMode.LAB.tracksAttention
        )
    }
}
