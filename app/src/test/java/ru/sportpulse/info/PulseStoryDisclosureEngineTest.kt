package ru.sportpulse.info

import org.junit.Assert.assertEquals
import org.junit.Test

class PulseStoryDisclosureEngineTest {
    @Test
    fun emptyOptionalToolsStayNeutralWhileBeaconRemainsVisible() {
        val summary = evaluate(
            checkpointIntegrity = StoryCheckpointIntegrity.EMPTY,
            checkpointChangeCount = null,
            threadIntegrity = StoryThreadIntegrity.EMPTY,
            threadStatus = null,
            beaconState = StoryBeaconState.ACTION_NOW,
            storyPhase = EventStoryPhase.PREPARING,
            completedChapterCount = 1
        )

        assertEquals("СИГНАЛЫ • 1", summary.badge)
        assertEquals(1, summary.alertCount)
        assertEquals(0, summary.dangerCount)
        assertEquals("не создана", summary.rows[0].value)
        assertEquals("не выбран", summary.rows[1].value)
        assertEquals("есть действие сейчас", summary.rows[2].value)
    }

    @Test
    fun changedCheckpointAndMovedThreadProduceTwoSignals() {
        val summary = evaluate(
            checkpointIntegrity = StoryCheckpointIntegrity.VALID,
            checkpointChangeCount = 3,
            threadIntegrity = StoryThreadIntegrity.VALID,
            threadStatus = StoryThreadStatus.MOVED,
            beaconState = StoryBeaconState.WATCHING,
            storyPhase = EventStoryPhase.READY,
            completedChapterCount = 3
        )

        assertEquals("СИГНАЛЫ • 2", summary.badge)
        assertEquals("изменений: 3", summary.rows[0].value)
        assertEquals("состояние изменилось", summary.rows[1].value)
        assertEquals("опорных точек: 2", summary.rows[2].value)
    }

    @Test
    fun damagedRecordsCannotLookLikeOrdinaryHistory() {
        val summary = evaluate(
            checkpointIntegrity = StoryCheckpointIntegrity.TAMPERED,
            checkpointChangeCount = null,
            threadIntegrity = StoryThreadIntegrity.TAMPERED,
            threadStatus = null,
            beaconState = StoryBeaconState.INCOMPLETE,
            storyPhase = EventStoryPhase.INCOMPLETE,
            completedChapterCount = 2
        )

        assertEquals("НУЖНА СВЕРКА • 4", summary.badge)
        assertEquals(4, summary.alertCount)
        assertEquals(3, summary.dangerCount)
        assertEquals(
            "Есть записи, которые нельзя считать надёжными",
            summary.headline
        )
    }

    @Test
    fun completedHistoryHasNoAlerts() {
        val summary = evaluate(
            checkpointIntegrity = StoryCheckpointIntegrity.VALID,
            checkpointChangeCount = 0,
            threadIntegrity = StoryThreadIntegrity.VALID,
            threadStatus = StoryThreadStatus.RESOLVED,
            beaconState = StoryBeaconState.COMPLETE,
            storyPhase = EventStoryPhase.COMPLETE,
            completedChapterCount = 6
        )

        assertEquals("БЕЗ НОВЫХ СИГНАЛОВ", summary.badge)
        assertEquals(0, summary.alertCount)
        assertEquals(
            List(4) { PulseStoryDisclosureTone.READY },
            summary.rows.map(PulseStoryDisclosureRow::tone)
        )
    }

    private fun evaluate(
        checkpointIntegrity: StoryCheckpointIntegrity,
        checkpointChangeCount: Int?,
        threadIntegrity: StoryThreadIntegrity,
        threadStatus: StoryThreadStatus?,
        beaconState: StoryBeaconState,
        storyPhase: EventStoryPhase,
        completedChapterCount: Int
    ): PulseStoryDisclosureSummary {
        return PulseStoryDisclosureEngine.evaluate(
            PulseStoryDisclosureInput(
                checkpointIntegrity = checkpointIntegrity,
                checkpointChangeCount = checkpointChangeCount,
                threadIntegrity = threadIntegrity,
                threadStatus = threadStatus,
                beaconState = beaconState,
                beaconMomentCount = 2,
                storyPhase = storyPhase,
                completedChapterCount = completedChapterCount
            )
        )
    }
}
