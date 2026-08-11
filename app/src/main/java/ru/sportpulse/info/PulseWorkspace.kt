package ru.sportpulse.info

internal enum class PulseWorkspaceMode {
    STORY,
    LAB;

    val tracksAttention: Boolean
        get() = this == LAB

    companion object {
        fun fromStored(value: String?): PulseWorkspaceMode {
            return value?.let {
                runCatching { valueOf(it) }.getOrNull()
            } ?: STORY
        }
    }
}
