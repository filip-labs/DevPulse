/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.debug

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager

// Temporary debug/test-only action. Remove after manual verification of INSERTED classification.
class DevPulseDebugInsertAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val caretOffset = editor.caretModel.offset

        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(caretOffset, DEBUG_INSERT_TEXT)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val hasEditor = project != null &&
            FileEditorManager.getInstance(project).selectedTextEditor != null

        event.presentation.isEnabled = hasEditor
    }

    private companion object {
        // Temporary debug/test-only content. Remove together with this action after manual verification.
        val DEBUG_INSERT_TEXT = """
            /*
             * DEV_PULSE_INSERTED_TEST_BLOCK
             * This block was inserted programmatically to test INSERTED edit classification.
             * It should be classified as INSERTED, not PASTED and not TYPED.
             */
            
        """.trimIndent()
    }
}
