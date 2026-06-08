/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "DevPulseSettings",
    storages = [Storage("devpulseSettings.xml")]
)
class DevPulseSettingsService : PersistentStateComponent<DevPulseSettingsState> {

    private var state = DevPulseSettingsState()

    override fun getState(): DevPulseSettingsState = state

    override fun loadState(state: DevPulseSettingsState) {
        XmlSerializerUtil.copyBean(state, this.state)
        normalize()
    }

    fun update(newState: DevPulseSettingsState) {
        state = normalizedCopy(newState)
    }

    fun snapshot(): DevPulseSettingsState = state.copy()

    fun workDurationSeconds(): Long = state.workDurationMinutes.toLong() * 60L

    fun shortBreakSeconds(): Long = state.shortBreakMinutes.toLong() * 60L

    fun longBreakSeconds(): Long = state.longBreakMinutes.toLong() * 60L

    private fun normalize() {
        state = normalizedCopy(state)
    }

    private fun normalizedCopy(source: DevPulseSettingsState): DevPulseSettingsState {
        return source.copy(
            workDurationMinutes = source.workDurationMinutes.coerceIn(
                MIN_WORK_DURATION_MINUTES,
                MAX_WORK_DURATION_MINUTES
            ),
            shortBreakMinutes = source.shortBreakMinutes.coerceIn(
                MIN_SHORT_BREAK_MINUTES,
                MAX_SHORT_BREAK_MINUTES
            ),
            longBreakMinutes = source.longBreakMinutes.coerceIn(
                MIN_LONG_BREAK_MINUTES,
                MAX_LONG_BREAK_MINUTES
            ),
            sessionsBeforeLongBreak = source.sessionsBeforeLongBreak.coerceIn(
                MIN_SESSIONS_BEFORE_LONG_BREAK,
                MAX_SESSIONS_BEFORE_LONG_BREAK
            )
        )
    }

    companion object {
        const val DEFAULT_WORK_DURATION_MINUTES = 25
        const val DEFAULT_SHORT_BREAK_MINUTES = 5
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val DEFAULT_SESSIONS_BEFORE_LONG_BREAK = 4

        const val MIN_WORK_DURATION_MINUTES = 1
        const val MAX_WORK_DURATION_MINUTES = 180
        const val MIN_SHORT_BREAK_MINUTES = 1
        const val MAX_SHORT_BREAK_MINUTES = 60
        const val MIN_LONG_BREAK_MINUTES = 1
        const val MAX_LONG_BREAK_MINUTES = 120
        const val MIN_SESSIONS_BEFORE_LONG_BREAK = 1
        const val MAX_SESSIONS_BEFORE_LONG_BREAK = 12
    }
}
