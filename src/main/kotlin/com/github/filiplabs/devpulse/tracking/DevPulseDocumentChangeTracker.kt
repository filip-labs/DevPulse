/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.tracking

import com.github.filiplabs.devpulse.model.EditEvent
import com.github.filiplabs.devpulse.services.DevPulseProjectLifecycleService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class DevPulseDocumentChangeTracker(
    private val project: Project
) {

    private val logger = thisLogger()
    private val started = AtomicBoolean(false)

    private val documentListener = object : DocumentListener {

        override fun documentChanged(event: DocumentEvent) {
            val activeFileTracker = project.service<ActiveFileTracker>()
            val pasteActionTracker = project.service<PasteActionTracker>()
            val statsService = project.service<DevPulseStatsService>()
            val filePath = activeFileTracker.getFilePath(event.document)
                ?: activeFileTracker.getActiveFilePath()
                ?: "unknown"

            val fileName = filePath.substringAfterLast('/')

            val addedCharacters = event.newLength
            val removedCharacters = event.oldLength
            val netChange = event.newLength - event.oldLength

            if (EditClassifier.shouldIgnore(addedCharacters, pasteActionTracker.isNonWritingActionInProgress())) {
                if (!pasteActionTracker.isNonWritingActionInProgress()) {
                    statsService.recordEditorActivity()
                }

                val ignoredMessage =
                    "DevPulse change ignored: $fileName: source=" +
                        if (pasteActionTracker.isNonWritingActionInProgress()) "IGNORED_ACTION" else "DELETION_ONLY" +
                        ", " +
                        "offset=${event.offset}, +$addedCharacters / -$removedCharacters, net=$netChange"

                logger.info(ignoredMessage)
                return
            }

            val editType = EditClassifier.classify(
                addedCharacters = addedCharacters,
                causedByPaste = pasteActionTracker.isPasteInProgress()
            )

            val editEvent = EditEvent(
                timestamp = Instant.now(),
                file = filePath,
                type = editType,
                characterCount = addedCharacters
            )

            statsService.recordEditEvent(editEvent)

            val message =
                "DevPulse classified edit event: $fileName: type=${editEvent.type}, " +
                    "offset=${event.offset}, +$addedCharacters / -$removedCharacters, net=$netChange"

            logger.info(message)
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        EditorFactory
            .getInstance()
            .eventMulticaster
            .addDocumentListener(documentListener, project.service<DevPulseProjectLifecycleService>())

        logger.info("DevPulse document change tracker started")
    }
}
