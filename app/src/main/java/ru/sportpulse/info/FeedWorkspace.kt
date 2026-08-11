package ru.sportpulse.info

internal enum class FeedWorkspaceMode {
    FOCUS,
    TOOLS;

    companion object {
        fun fromStored(value: String?): FeedWorkspaceMode {
            return value?.let {
                runCatching { valueOf(it) }.getOrNull()
            } ?: FOCUS
        }
    }
}
