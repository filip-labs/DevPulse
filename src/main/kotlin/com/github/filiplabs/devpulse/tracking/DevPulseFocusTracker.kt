/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.tracking

import com.github.filiplabs.devpulse.model.FocusStatus
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class DevPulseFocusTracker(
    private val project: Project
) : Disposable {

    private val logger = thisLogger()
    private val started = AtomicBoolean(false)
    private val focusStatus = AtomicReference(FocusStatus.NO_FILE)

    @Volatile
    private var focusTickFuture: ScheduledFuture<*>? = null

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        focusTickFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { runTickSafely() },
            1L,
            1L,
            TimeUnit.SECONDS
        )

        logger.info("DevPulse focus tracker started")
    }

    fun getStatus(): FocusStatus = focusStatus.get()

    override fun dispose() {
        focusTickFuture?.cancel(false)
    }

    private fun runTickSafely() {
        if (project.isDisposed) {
            return
        }

        try {
            tick()
        } catch (t: Throwable) {
            logger.warn("DevPulse focus tracker tick failed", t)
        }
    }

    private fun tick() {
        val activeFileTracker = project.service<ActiveFileTracker>()
        val statsService = project.service<DevPulseStatsService>()
        val activeFilePath = activeFileTracker.getActiveFilePath()

        val nextStatus = when {
            activeFilePath.isNullOrBlank() -> FocusStatus.NO_FILE
            statsService.isIdle() -> FocusStatus.IDLE
            else -> FocusStatus.ACTIVE
        }

        updateStatus(nextStatus)

        if (nextStatus == FocusStatus.ACTIVE && activeFilePath != null) {
            statsService.addFocusSeconds(activeFilePath)
        }
    }

    private fun updateStatus(nextStatus: FocusStatus) {
        val previousStatus = focusStatus.getAndSet(nextStatus)
        if (previousStatus != nextStatus) {
            logger.info("DevPulse focus tracker status changed: $nextStatus")
        }
    }
}
