/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.tracking

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean

class PasteActionTracker(
    private val project: Project
) {

    private val logger = thisLogger()

    private val pasteInProgress = AtomicBoolean(false)

    fun start() {
        ApplicationManager
            .getApplication()
            .messageBus
            .connect(project)
            .subscribe(
                AnActionListener.TOPIC,
                object : AnActionListener {

                    override fun beforeActionPerformed(
                        action: AnAction,
                        event: AnActionEvent
                    ) {
                        val actionId = event.actionManager.getId(action) ?: "unknown"

                        if (isPasteAction(actionId)) {
                            pasteInProgress.set(true)

                            logger.info("DevPulse paste action started: id=$actionId")

                            // Uncomment for manual sandbox testing.
                            // This prints paste action start directly in the runIde terminal output.
                            println("DevPulse paste action started: id=$actionId")
                        }
                    }

                    override fun afterActionPerformed(
                        action: AnAction,
                        event: AnActionEvent,
                        result: AnActionResult
                    ) {
                        val actionId = event.actionManager.getId(action) ?: "unknown"

                        if (isPasteAction(actionId)) {
                            pasteInProgress.set(false)

                            logger.info("DevPulse paste action finished: id=$actionId")

                            // Uncomment for manual sandbox testing.
                            // This prints paste action finish directly in the runIde terminal output.
                            println("DevPulse paste action finished: id=$actionId")
                        }
                    }
                }
            )

        logger.info("DevPulse paste action tracker started")

        // Uncomment for manual sandbox testing.
        // This prints paste action tracker startup directly in the runIde terminal output.
        println("DevPulse paste action tracker started")
    }

    fun isPasteInProgress(): Boolean {
        return pasteInProgress.get()
    }

    private fun isPasteAction(actionId: String): Boolean {
        return actionId == "EditorPaste" || actionId == "\$Paste"
    }
}
