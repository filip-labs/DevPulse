package com.github.filiplabs.devpulse.startup

import com.github.filiplabs.devpulse.tracking.DevPulseDocumentChangeTracker
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        DevPulseDocumentChangeTracker(project).start()
    }
}