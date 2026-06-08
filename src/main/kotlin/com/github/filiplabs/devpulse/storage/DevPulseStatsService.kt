/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.storage

import com.github.filiplabs.devpulse.model.DayStats
import com.github.filiplabs.devpulse.model.EditEvent
import com.github.filiplabs.devpulse.model.EditType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.time.LocalDate

@Service(Service.Level.PROJECT)
@State(
    name = "DevPulseStatsService",
    storages = [Storage("devpulse-stats.xml")]
)
class DevPulseStatsService(
    private val project: Project
) : PersistentStateComponent<DevPulseStatsService.PersistenceState> {

    private val logger = thisLogger()
    private val lock = Any()

    @Volatile
    private var lastEditorActivityAtMillis = System.currentTimeMillis()

    private var currentDate = today()
    private val focusSecondsByFile = linkedMapOf<String, Long>()
    private var typedCharacters = 0
    private var pastedCharacters = 0
    private var insertedCharacters = 0
    private var pomodoroCompletedSessions = 0

    override fun getState(): PersistenceState {
        synchronized(lock) {
            rolloverIfNeededLocked()

            return PersistenceState().apply {
                date = currentDate
                focusEntries = focusSecondsByFile.entries
                    .map { FocusEntryState(it.key, it.value) }
                    .toMutableList()
                typedCharacters = this@DevPulseStatsService.typedCharacters
                pastedCharacters = this@DevPulseStatsService.pastedCharacters
                insertedCharacters = this@DevPulseStatsService.insertedCharacters
                pomodoroCompletedSessions = this@DevPulseStatsService.pomodoroCompletedSessions
            }
        }
    }

    override fun loadState(state: PersistenceState) {
        synchronized(lock) {
            currentDate = state.date.ifBlank { today() }
            focusSecondsByFile.clear()
            state.focusEntries.forEach { entry ->
                if (entry.file.isNotBlank()) {
                    focusSecondsByFile[entry.file] = entry.seconds
                }
            }
            typedCharacters = state.typedCharacters
            pastedCharacters = state.pastedCharacters
            insertedCharacters = state.insertedCharacters
            pomodoroCompletedSessions = state.pomodoroCompletedSessions
            rolloverIfNeededLocked()
        }

        logger.info("DevPulse stats loaded for project: ${project.name}")
    }

    fun recordEditEvent(event: EditEvent) {
        synchronized(lock) {
            rolloverIfNeededLocked()
            lastEditorActivityAtMillis = System.currentTimeMillis()

            when (event.type) {
                EditType.TYPED -> typedCharacters += event.characterCount
                EditType.PASTED -> pastedCharacters += event.characterCount
                EditType.INSERTED -> insertedCharacters += event.characterCount
            }
        }
    }

    fun recordEditorActivity(fileOrClass: String? = null) {
        synchronized(lock) {
            rolloverIfNeededLocked()
            lastEditorActivityAtMillis = System.currentTimeMillis()
        }
    }

    fun addFocusSeconds(
        fileOrClass: String,
        seconds: Long = 1L
    ) {
        if (fileOrClass.isBlank() || seconds <= 0) {
            return
        }

        val totalForFile: Long
        synchronized(lock) {
            rolloverIfNeededLocked()
            totalForFile = (focusSecondsByFile[fileOrClass] ?: 0L) + seconds
            focusSecondsByFile[fileOrClass] = totalForFile
        }

        logger.info(
            "DevPulse focus seconds added: file=${displayName(fileOrClass)}, " +
                "seconds=$seconds, total=$totalForFile"
        )
    }

    fun incrementCompletedPomodoroSessions() {
        synchronized(lock) {
            rolloverIfNeededLocked()
            pomodoroCompletedSessions += 1
        }
    }

    fun getTodayStats(): DayStats {
        synchronized(lock) {
            rolloverIfNeededLocked()

            return DayStats(
                date = currentDate,
                timePerFileOrClass = LinkedHashMap(focusSecondsByFile),
                editCountersByType = mapOf(
                    EditType.TYPED to typedCharacters,
                    EditType.PASTED to pastedCharacters,
                    EditType.INSERTED to insertedCharacters
                ),
                pomodoroCompletedSessions = pomodoroCompletedSessions
            )
        }
    }

    fun hasAnyData(): Boolean {
        val stats = getTodayStats()
        return stats.totalFocusSeconds > 0 ||
            stats.totalWrittenCharacters > 0 ||
            stats.pomodoroCompletedSessions > 0
    }

    fun isIdle(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastActivity = lastEditorActivityAtMillis
        return nowMillis - lastActivity >= IDLE_TIMEOUT_MILLIS
    }

    private fun rolloverIfNeededLocked() {
        val today = today()
        if (currentDate == today) {
            return
        }

        currentDate = today
        focusSecondsByFile.clear()
        typedCharacters = 0
        pastedCharacters = 0
        insertedCharacters = 0
        pomodoroCompletedSessions = 0
        lastEditorActivityAtMillis = System.currentTimeMillis()

        logger.info("DevPulse daily stats rolled over: date=$currentDate")
    }

    private fun today(): String = LocalDate.now().toString()

    private fun displayName(fileOrClass: String): String {
        return File(fileOrClass).name.ifBlank { fileOrClass }
    }

    companion object {
        const val IDLE_TIMEOUT_MINUTES = 5L
        private const val IDLE_TIMEOUT_MILLIS = IDLE_TIMEOUT_MINUTES * 60_000L
    }

    class PersistenceState {
        var date: String = ""
        var focusEntries: MutableList<FocusEntryState> = mutableListOf()
        var typedCharacters: Int = 0
        var pastedCharacters: Int = 0
        var insertedCharacters: Int = 0
        var pomodoroCompletedSessions: Int = 0
    }

    class FocusEntryState() {
        var file: String = ""
        var seconds: Long = 0L

        constructor(
            file: String,
            seconds: Long
        ) : this() {
            this.file = file
            this.seconds = seconds
        }
    }
}
