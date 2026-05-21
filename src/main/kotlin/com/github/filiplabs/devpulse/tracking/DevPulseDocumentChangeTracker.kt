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
            val filePath = activeFileTracker.getFilePath(event.document)
                ?: activeFileTracker.getActiveFilePath()
                ?: "unknown"

            val fileName = filePath.substringAfterLast('/')

            val addedCharacters = event.newLength
            val removedCharacters = event.oldLength
            val netChange = event.newLength - event.oldLength

            val message =
                "DevPulse change: $fileName: offset=${event.offset}, " +
                        "+$addedCharacters / -$removedCharacters, net=$netChange"

            logger.info(message)

            // Uncomment for manual sandbox testing.
            // This prints structured document change events directly in the runIde terminal output.
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
