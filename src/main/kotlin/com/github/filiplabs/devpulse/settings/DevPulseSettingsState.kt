/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.settings

data class DevPulseSettingsState(
    var workDurationMinutes: Int = DevPulseSettingsService.DEFAULT_WORK_DURATION_MINUTES,
    var shortBreakMinutes: Int = DevPulseSettingsService.DEFAULT_SHORT_BREAK_MINUTES,
    var longBreakMinutes: Int = DevPulseSettingsService.DEFAULT_LONG_BREAK_MINUTES,
    var sessionsBeforeLongBreak: Int = DevPulseSettingsService.DEFAULT_SESSIONS_BEFORE_LONG_BREAK,
    var autoStartNextSession: Boolean = false,
    var memoryMode: DevPulseMemoryMode = DevPulseMemoryMode.KEEP_UNTIL_IDE_CLOSE,
    var resetStatsOnProjectOpen: Boolean = false
)
