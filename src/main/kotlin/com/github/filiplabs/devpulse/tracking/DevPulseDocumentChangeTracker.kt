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
import com.github.filiplabs.devpulse.model.EditType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import java.time.Instant

class DevPulseDocumentChangeTracker(
    private val disposable: Disposable,
    private val activeFileTracker: ActiveFileTracker,
    private val pasteActionTracker: PasteActionTracker
) {

    private val logger = thisLogger()

    private val documentListener = object : DocumentListener {

        override fun documentChanged(event: DocumentEvent) {
            val filePath = activeFileTracker.getFilePath(event.document)
                ?: activeFileTracker.getActiveFilePath()
                ?: "unknown"

            val fileName = filePath.substringAfterLast('/')

            val addedCharacters = event.newLength
            val removedCharacters = event.oldLength
            val netChange = event.newLength - event.oldLength

            if (pasteActionTracker.isNonWritingActionInProgress()) {
                val ignoredMessage =
                    "DevPulse change ignored: $fileName: source=IGNORED_ACTION, " +
                        "offset=${event.offset}, +$addedCharacters / -$removedCharacters, net=$netChange"

                logger.info(ignoredMessage)

                // Uncomment for manual sandbox testing.
                // This prints ignored document change events directly in the runIde terminal output.
                // println(ignoredMessage)
                return
            }

            val editType = classifyEdit(
                addedCharacters = addedCharacters,
                causedByPaste = pasteActionTracker.isPasteInProgress()
            )

            val editEvent = EditEvent(
                timestamp = Instant.now(),
                file = filePath,
                type = editType,
                characterCount = addedCharacters
            )

            val message =
                "DevPulse change: $fileName: type=${editEvent.type}, " +
                        "offset=${event.offset}, +$addedCharacters / -$removedCharacters, net=$netChange"

            logger.info(message)

            // Uncomment for manual sandbox testing.
            // This prints classified document change events directly in the runIde terminal output.
            // println(message)
        }
    }

    fun start() {
        EditorFactory
            .getInstance()
            .eventMulticaster
            .addDocumentListener(documentListener, disposable)

        logger.info("DevPulse document change tracker started")

        // Uncomment for manual sandbox testing.
        // This prints tracker startup directly in the runIde terminal output.
        // println("DevPulse document change tracker started")
    }

    private fun classifyEdit(
        addedCharacters: Int,
        causedByPaste: Boolean
    ): EditType {
        if (causedByPaste) {
            return EditType.PASTED
        }

        return if (addedCharacters <= TYPED_CHARACTER_THRESHOLD) {
            EditType.TYPED
        } else {
            EditType.INSERTED
        }
    }

    private companion object {
        const val TYPED_CHARACTER_THRESHOLD = 2
    }
}