/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.tracking

import com.github.filiplabs.devpulse.services.DevPulseProjectLifecycleService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class ActiveFileTracker(
    private val project: Project
) {

    private val logger = thisLogger()
    private val started = AtomicBoolean(false)

    @Volatile
    private var activeFile: VirtualFile? = null

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        updateActiveFile(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())

        project.messageBus
            .connect(project.service<DevPulseProjectLifecycleService>())
            .subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {

                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        updateActiveFile(event.newFile)
                    }
                }
            )

        logger.info("DevPulse active file tracker started")
    }

    fun getActiveFilePath(): String? {
        return activeFile?.path
    }

    fun getFilePath(document: Document): String? {
        return FileDocumentManager.getInstance()
            .getFile(document)
            ?.path
    }

    private fun updateActiveFile(file: VirtualFile?) {
        activeFile = file

        val path = file?.path ?: "unknown"
        if (file != null) {
            project.service<DevPulseStatsService>().recordEditorActivity()
        }

        logger.info("DevPulse active file changed: $path")
    }
}
