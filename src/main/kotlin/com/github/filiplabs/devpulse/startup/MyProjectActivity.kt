/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */

package com.github.filiplabs.devpulse.startup

import com.github.filiplabs.devpulse.tracking.DevPulseDocumentChangeTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        DevPulseDocumentChangeTracker(project).start()
    }
}
