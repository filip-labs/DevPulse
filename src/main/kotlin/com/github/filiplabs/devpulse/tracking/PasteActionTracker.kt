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

class PasteActionTracker(
    private val project: Project
) {

    private val logger = thisLogger()

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

                        logger.info("DevPulse action started: id=$actionId")

                        // Uncomment for manual sandbox testing.
                        // This prints action IDs directly in the runIde terminal output.
                        // println("DevPulse action started: id=$actionId")

                        if (isPasteAction(actionId)) {
                            logger.info("DevPulse paste action started: id=$actionId")

                            // Uncomment for manual sandbox testing.
                            // println("DevPulse paste action started: id=$actionId")
                        }
                    }

                    override fun afterActionPerformed(
                        action: AnAction,
                        event: AnActionEvent,
                        result: AnActionResult
                    ) {
                        val actionId = event.actionManager.getId(action) ?: "unknown"

                        logger.info("DevPulse action finished: id=$actionId")

                        // Uncomment for manual sandbox testing.
                        // This prints action IDs directly in the runIde terminal output.
                        // println("DevPulse action finished: id=$actionId")

                        if (isPasteAction(actionId)) {
                            logger.info("DevPulse paste action finished: id=$actionId")

                            // Uncomment for manual sandbox testing.
                            // println("DevPulse paste action finished: id=$actionId")
                        }
                    }
                }
            )

        logger.info("DevPulse paste action tracker started")

        // Uncomment for manual sandbox testing.
        // println("DevPulse paste action tracker started")
    }

    private fun isPasteAction(actionId: String): Boolean {
        return actionId == "EditorPaste" || actionId == "\$Paste"
    }
}