/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.services

import com.github.filiplabs.devpulse.model.PomodoroSnapshot
import com.github.filiplabs.devpulse.model.PomodoroState
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class DevPulsePomodoroService(
    private val project: Project
) : Disposable {

    private val logger = thisLogger()
    private val lock = Any()

    private var state = PomodoroState.IDLE
    private var remainingSeconds = WORK_DURATION_SECONDS

    @Volatile
    private var tickerFuture: ScheduledFuture<*>? = null

    fun start() {
        var shouldLog = false
        synchronized(lock) {
            if (state == PomodoroState.IDLE) {
                state = PomodoroState.WORK
                remainingSeconds = WORK_DURATION_SECONDS
                ensureTickerLocked()
                shouldLog = true
            }
        }

        if (shouldLog) {
            logger.info("DevPulse pomodoro state changed: WORK")
        }
    }

    fun stop() {
        synchronized(lock) {
            cancelTickerLocked()
            state = PomodoroState.IDLE
            remainingSeconds = WORK_DURATION_SECONDS
        }

        logger.info("DevPulse pomodoro state changed: IDLE")
    }

    fun reset() {
        synchronized(lock) {
            cancelTickerLocked()
            state = PomodoroState.IDLE
            remainingSeconds = WORK_DURATION_SECONDS
        }

        logger.info("DevPulse pomodoro reset")
    }

    fun toggle() {
        if (getSnapshot().state == PomodoroState.IDLE) {
            start()
        } else {
            stop()
        }
    }

    fun getSnapshot(): PomodoroSnapshot {
        synchronized(lock) {
            return PomodoroSnapshot(
                state = state,
                remainingSeconds = remainingSeconds
            )
        }
    }

    override fun dispose() {
        synchronized(lock) {
            cancelTickerLocked()
        }
    }

    private fun ensureTickerLocked() {
        if (tickerFuture != null && !tickerFuture!!.isCancelled) {
            return
        }

        tickerFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { runTickSafely() },
            1L,
            1L,
            TimeUnit.SECONDS
        )
    }

    private fun cancelTickerLocked() {
        tickerFuture?.cancel(false)
        tickerFuture = null
    }

    private fun runTickSafely() {
        if (project.isDisposed) {
            return
        }

        try {
            tick()
        } catch (t: Throwable) {
            logger.warn("DevPulse pomodoro tick failed", t)
        }
    }

    private fun tick() {
        var completedWorkSession = false
        var stateChangeMessage: String? = null

        synchronized(lock) {
            if (state == PomodoroState.IDLE) {
                cancelTickerLocked()
                return
            }

            if (remainingSeconds > 0) {
                remainingSeconds -= 1
            }

            if (remainingSeconds > 0) {
                return
            }

            when (state) {
                PomodoroState.WORK -> {
                    completedWorkSession = true
                    state = PomodoroState.BREAK
                    remainingSeconds = BREAK_DURATION_SECONDS
                    stateChangeMessage = "DevPulse pomodoro state changed: BREAK"
                }

                PomodoroState.BREAK -> {
                    cancelTickerLocked()
                    state = PomodoroState.IDLE
                    remainingSeconds = WORK_DURATION_SECONDS
                    stateChangeMessage = "DevPulse pomodoro state changed: IDLE"
                }

                PomodoroState.IDLE -> Unit
            }
        }

        if (completedWorkSession) {
            project.service<DevPulseStatsService>().incrementCompletedPomodoroSessions()
            logger.info("DevPulse pomodoro work session completed")
        }

        if (stateChangeMessage != null) {
            logger.info(stateChangeMessage)
        }
    }

    companion object {
        const val WORK_DURATION_SECONDS = 10L
        const val BREAK_DURATION_SECONDS = 5L
    }
}
