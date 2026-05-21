/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */

package com.github.filiplabs.devpulse.tracking

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

class DevPulseDocumentChangeTracker(
    private val disposable: Disposable,
    private val activeFileTracker: ActiveFileTracker
) {

    private val logger = thisLogger()

    private val documentListener = object : DocumentListener {

        override fun documentChanged(event: DocumentEvent) {
            val eventFilePath = activeFileTracker.getFilePath(event.document) ?: "unknown"
            val activeFilePath = activeFileTracker.getActiveFilePath() ?: "unknown"

            val message =
                "DevPulse document changed: file=$eventFilePath, activeFile=$activeFilePath, " +
                        "offset=${event.offset}, oldLength=${event.oldLength}, newLength=${event.newLength}"

            logger.info(message)

            // Uncomment for manual sandbox testing.
            // This prints document change events directly in the runIde terminal output.
            //println(message)
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
        //println("DevPulse document change tracker started")
    }
}
