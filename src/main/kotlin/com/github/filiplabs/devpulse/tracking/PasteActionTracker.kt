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
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class PasteActionTracker(
    private val project: Project
) {

    private val logger = thisLogger()
    private val started = AtomicBoolean(false)

    private val pasteInProgress = AtomicBoolean(false)
    private val nonWritingActionInProgress = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

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
                            // This prints paste action starts directly in the runIde terminal output.
                            // println("DevPulse paste action started: id=$actionId")
                        }

                        if (isNonWritingAction(actionId)) {
                            nonWritingActionInProgress.set(true)

                            logger.info("DevPulse non-writing action started: id=$actionId")

                            // Uncomment for manual sandbox testing.
                            // This prints non-writing action start directly in the runIde terminal output.
                            // println("DevPulse non-writing action started: id=$actionId")
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
                            // println("DevPulse paste action finished: id=$actionId")
                        }

                        if (isNonWritingAction(actionId)) {
                            nonWritingActionInProgress.set(false)

                            logger.info("DevPulse non-writing action finished: id=$actionId")

                            // Uncomment for manual sandbox testing.
                            // This prints non-writing action finish directly in the runIde terminal output.
                            // println("DevPulse non-writing action finished: id=$actionId")
                        }
                    }
                }
            )

        logger.info("DevPulse paste action tracker started")

        // Uncomment for manual sandbox testing.
        // This prints paste action tracker startup directly in the runIde terminal output.
        // println("DevPulse paste action tracker started")
    }

    fun isPasteInProgress(): Boolean {
        return pasteInProgress.get()
    }

    fun isNonWritingActionInProgress(): Boolean {
        return nonWritingActionInProgress.get()
    }

    private fun isPasteAction(actionId: String): Boolean {
        return actionId in PASTE_ACTION_IDS || actionId.contains("Paste", ignoreCase = true)
    }

    private fun isNonWritingAction(actionId: String): Boolean {
        return actionId in NON_WRITING_ACTION_IDS ||
            actionId.contains("Undo", ignoreCase = true) ||
            actionId.contains("Redo", ignoreCase = true)
    }

    private companion object {
        val PASTE_ACTION_IDS = setOf("EditorPaste", "\$Paste")

        val NON_WRITING_ACTION_IDS = setOf(
            "\$Undo",
            "\$Redo",
            "Undo",
            "Redo",
            "EditorUndo",
            "EditorRedo",
            "ReformatCode",
            "OptimizeImports"
        )
    }
}
