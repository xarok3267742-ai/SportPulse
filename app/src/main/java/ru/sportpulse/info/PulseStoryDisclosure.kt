package ru.sportpulse.info

internal enum class PulseStoryDisclosureTone {
    NEUTRAL,
    INFO,
    READY,
    ATTENTION,
    DANGER
}

internal data class PulseStoryDisclosureRow(
    val title: String,
    val value: String,
    val tone: PulseStoryDisclosureTone
) {
    init {
        require(title.isNotBlank())
        require(value.isNotBlank())
    }
}

internal data class PulseStoryDisclosureInput(
    val checkpointIntegrity: StoryCheckpointIntegrity,
    val checkpointChangeCount: Int?,
    val threadIntegrity: StoryThreadIntegrity,
    val threadStatus: StoryThreadStatus?,
    val beaconState: StoryBeaconState,
    val beaconMomentCount: Int,
    val storyPhase: EventStoryPhase,
    val completedChapterCount: Int
) {
    init {
        require(
            (checkpointIntegrity == StoryCheckpointIntegrity.VALID) ==
                (checkpointChangeCount != null)
        )
        require(checkpointChangeCount == null || checkpointChangeCount >= 0)
        require(
            (threadIntegrity == StoryThreadIntegrity.VALID) ==
                (threadStatus != null)
        )
        require(beaconMomentCount in 0..5)
        require(completedChapterCount in 0..6)
    }
}

internal data class PulseStoryDisclosureSummary(
    val badge: String,
    val headline: String,
    val body: String,
    val rows: List<PulseStoryDisclosureRow>,
    val alertCount: Int,
    val dangerCount: Int
) {
    init {
        require(badge.isNotBlank())
        require(headline.isNotBlank())
        require(body.isNotBlank())
        require(rows.size == 4)
        require(alertCount >= dangerCount)
        require(dangerCount >= 0)
    }
}

internal object PulseStoryDisclosureEngine {
    fun evaluate(
        input: PulseStoryDisclosureInput
    ): PulseStoryDisclosureSummary {
        val rows = listOf(
            checkpointRow(input),
            threadRow(input),
            beaconRow(input),
            chaptersRow(input)
        )
        val dangerCount = rows.count {
            it.tone == PulseStoryDisclosureTone.DANGER
        }
        val alertCount = dangerCount + rows.count {
            it.tone == PulseStoryDisclosureTone.ATTENTION
        }
        val badge = when {
            dangerCount > 0 -> "НУЖНА СВЕРКА • $alertCount"
            alertCount > 0 -> "СИГНАЛЫ • $alertCount"
            else -> "БЕЗ НОВЫХ СИГНАЛОВ"
        }
        val headline = when {
            dangerCount > 0 ->
                "Есть записи, которые нельзя считать надёжными"
            alertCount > 0 ->
                "Есть дополнительные сигналы контроля"
            else -> "История без новых предупреждений"
        }
        return PulseStoryDisclosureSummary(
            badge = badge,
            headline = headline,
            body = "Контрольная точка, личный вопрос, временной маяк и полное досье скрыты до явной команды. Они не меняют короткий итог выше.",
            rows = rows,
            alertCount = alertCount,
            dangerCount = dangerCount
        )
    }

    private fun checkpointRow(
        input: PulseStoryDisclosureInput
    ): PulseStoryDisclosureRow {
        val count = input.checkpointChangeCount
        return when (input.checkpointIntegrity) {
            StoryCheckpointIntegrity.EMPTY -> row(
                title = "Контрольная точка",
                value = "не создана",
                tone = PulseStoryDisclosureTone.NEUTRAL
            )
            StoryCheckpointIntegrity.VALID -> if (count == 0) {
                row(
                    title = "Контрольная точка",
                    value = "изменений нет",
                    tone = PulseStoryDisclosureTone.READY
                )
            } else {
                row(
                    title = "Контрольная точка",
                    value = "изменений: $count",
                    tone = PulseStoryDisclosureTone.ATTENTION
                )
            }
            StoryCheckpointIntegrity.TAMPERED -> row(
                title = "Контрольная точка",
                value = "запись повреждена",
                tone = PulseStoryDisclosureTone.DANGER
            )
        }
    }

    private fun threadRow(
        input: PulseStoryDisclosureInput
    ): PulseStoryDisclosureRow {
        return when (input.threadIntegrity) {
            StoryThreadIntegrity.EMPTY -> row(
                title = "Личный вопрос",
                value = "не выбран",
                tone = PulseStoryDisclosureTone.NEUTRAL
            )
            StoryThreadIntegrity.TAMPERED -> row(
                title = "Личный вопрос",
                value = "запись повреждена",
                tone = PulseStoryDisclosureTone.DANGER
            )
            StoryThreadIntegrity.VALID -> when (
                checkNotNull(input.threadStatus)
            ) {
                StoryThreadStatus.OPEN -> row(
                    title = "Личный вопрос",
                    value = "вопрос открыт",
                    tone = PulseStoryDisclosureTone.INFO
                )
                StoryThreadStatus.MOVED -> row(
                    title = "Личный вопрос",
                    value = "состояние изменилось",
                    tone = PulseStoryDisclosureTone.ATTENTION
                )
                StoryThreadStatus.RESOLVED -> row(
                    title = "Личный вопрос",
                    value = "вопрос закрыт",
                    tone = PulseStoryDisclosureTone.READY
                )
                StoryThreadStatus.MISSED -> row(
                    title = "Личный вопрос",
                    value = "момент пропущен",
                    tone = PulseStoryDisclosureTone.DANGER
                )
            }
        }
    }

    private fun beaconRow(
        input: PulseStoryDisclosureInput
    ): PulseStoryDisclosureRow {
        return when (input.beaconState) {
            StoryBeaconState.NO_TIMELINE -> row(
                title = "Временной маяк",
                value = "время не подтверждено",
                tone = PulseStoryDisclosureTone.ATTENTION
            )
            StoryBeaconState.ACTION_NOW -> row(
                title = "Временной маяк",
                value = "есть действие сейчас",
                tone = PulseStoryDisclosureTone.ATTENTION
            )
            StoryBeaconState.WATCHING -> row(
                title = "Временной маяк",
                value = "опорных точек: ${input.beaconMomentCount}",
                tone = PulseStoryDisclosureTone.INFO
            )
            StoryBeaconState.EVENT_ACTIVE -> row(
                title = "Временной маяк",
                value = "событие идёт",
                tone = PulseStoryDisclosureTone.INFO
            )
            StoryBeaconState.REVIEW_DUE -> row(
                title = "Временной маяк",
                value = "пора к разбору",
                tone = PulseStoryDisclosureTone.ATTENTION
            )
            StoryBeaconState.COMPLETE -> row(
                title = "Временной маяк",
                value = "маршрут закрыт",
                tone = PulseStoryDisclosureTone.READY
            )
            StoryBeaconState.INCOMPLETE -> row(
                title = "Временной маяк",
                value = "будущих точек нет",
                tone = PulseStoryDisclosureTone.ATTENTION
            )
        }
    }

    private fun chaptersRow(
        input: PulseStoryDisclosureInput
    ): PulseStoryDisclosureRow {
        val tone = when (input.storyPhase) {
            EventStoryPhase.COMPLETE -> PulseStoryDisclosureTone.READY
            EventStoryPhase.INCOMPLETE -> PulseStoryDisclosureTone.DANGER
            else -> PulseStoryDisclosureTone.INFO
        }
        val value = if (input.storyPhase == EventStoryPhase.INCOMPLETE) {
            "маршрут завершён не полностью"
        } else {
            "готово ${input.completedChapterCount} из 6"
        }
        return row(
            title = "Главы маршрута",
            value = value,
            tone = tone
        )
    }

    private fun row(
        title: String,
        value: String,
        tone: PulseStoryDisclosureTone
    ): PulseStoryDisclosureRow {
        return PulseStoryDisclosureRow(
            title = title,
            value = value,
            tone = tone
        )
    }
}
