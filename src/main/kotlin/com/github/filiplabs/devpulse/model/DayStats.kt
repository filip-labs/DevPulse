/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.model

data class DayStats(
    val date: String,
    val timePerFileOrClass: Map<String, Long>,
    val editCountersByType: Map<EditType, Int>,
    val pomodoroCompletedSessions: Int
) {
    val totalFocusSeconds: Long
        get() = timePerFileOrClass.values.sum()

    val totalWrittenCharacters: Int
        get() = editCountersByType.values.sum()
}
