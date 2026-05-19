package com.github.filiplabs.devpulse.tracking

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

class DevPulseDocumentChangeTracker(
    private val disposable: Disposable
) {

    private val logger = thisLogger()

    private val documentListener = object : DocumentListener {

        override fun documentChanged(event: DocumentEvent) {
            logger.info(
                "DevPulse document changed: offset=${event.offset}, oldLength=${event.oldLength}, newLength=${event.newLength}"
            )
        }
    }

    fun start() {
        EditorFactory
            .getInstance()
            .eventMulticaster
            .addDocumentListener(documentListener, disposable)

        logger.info("DevPulse document change tracker started")
    }
}