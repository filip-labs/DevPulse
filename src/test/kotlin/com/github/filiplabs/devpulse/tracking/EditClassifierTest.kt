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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditClassifierTest {

    @Test
    fun classifiesPasteEditsAsPasted() {
        assertEquals(EditType.PASTED, EditClassifier.classify(8, causedByPaste = true))
    }

    @Test
    fun classifiesSmallNonPasteEditsAsTyped() {
        assertEquals(EditType.TYPED, EditClassifier.classify(1, causedByPaste = false))
        assertEquals(EditType.TYPED, EditClassifier.classify(2, causedByPaste = false))
    }

    @Test
    fun classifiesLargeNonPasteEditsAsInserted() {
        assertEquals(EditType.INSERTED, EditClassifier.classify(3, causedByPaste = false))
    }

    @Test
    fun ignoresDeletionOnlyAndIgnoredActions() {
        assertTrue(EditClassifier.shouldIgnore(0, causedByNonWritingAction = false))
        assertTrue(EditClassifier.shouldIgnore(4, causedByNonWritingAction = true))
    }
}
