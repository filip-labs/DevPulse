/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DevPulseSettingsServiceTest {

    @Test
    fun usesExpectedDefaults() {
        val settings = DevPulseSettingsService().snapshot()

        assertEquals(25, settings.workDurationMinutes)
        assertEquals(5, settings.shortBreakMinutes)
        assertEquals(15, settings.longBreakMinutes)
        assertEquals(4, settings.sessionsBeforeLongBreak)
        assertFalse(settings.autoStartNextSession)
        assertEquals(DevPulseMemoryMode.KEEP_UNTIL_IDE_CLOSE, settings.memoryMode)
        assertFalse(settings.resetStatsOnProjectOpen)
    }

    @Test
    fun clampsInvalidLoadedValues() {
        val service = DevPulseSettingsService()

        service.loadState(
            DevPulseSettingsState(
                workDurationMinutes = -10,
                shortBreakMinutes = 0,
                longBreakMinutes = 500,
                sessionsBeforeLongBreak = 99
            )
        )

        val settings = service.snapshot()
        assertEquals(DevPulseSettingsService.MIN_WORK_DURATION_MINUTES, settings.workDurationMinutes)
        assertEquals(DevPulseSettingsService.MIN_SHORT_BREAK_MINUTES, settings.shortBreakMinutes)
        assertEquals(DevPulseSettingsService.MAX_LONG_BREAK_MINUTES, settings.longBreakMinutes)
        assertEquals(
            DevPulseSettingsService.MAX_SESSIONS_BEFORE_LONG_BREAK,
            settings.sessionsBeforeLongBreak
        )
    }

    @Test
    fun reportsConfiguredDurationsInSeconds() {
        val service = DevPulseSettingsService()
        service.update(
            DevPulseSettingsState(
                workDurationMinutes = 30,
                shortBreakMinutes = 7,
                longBreakMinutes = 20
            )
        )

        assertEquals(1_800L, service.workDurationSeconds())
        assertEquals(420L, service.shortBreakSeconds())
        assertEquals(1_200L, service.longBreakSeconds())
    }
}
