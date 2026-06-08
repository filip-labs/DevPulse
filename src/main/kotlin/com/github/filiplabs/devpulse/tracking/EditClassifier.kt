/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.tracking

import com.github.filiplabs.devpulse.model.EditType

object EditClassifier {

    const val TYPED_CHARACTER_THRESHOLD = 2

    fun shouldIgnore(
        addedCharacters: Int,
        causedByNonWritingAction: Boolean
    ): Boolean {
        return causedByNonWritingAction || addedCharacters <= 0
    }

    fun classify(
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
}
