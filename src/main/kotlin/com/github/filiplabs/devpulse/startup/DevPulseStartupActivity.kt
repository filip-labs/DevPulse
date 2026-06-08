/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.startup

import com.github.filiplabs.devpulse.services.DevPulsePomodoroService
import com.github.filiplabs.devpulse.settings.DevPulseSettingsService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.github.filiplabs.devpulse.tracking.ActiveFileTracker
import com.github.filiplabs.devpulse.tracking.DevPulseDocumentChangeTracker
import com.github.filiplabs.devpulse.tracking.DevPulseFocusTracker
import com.github.filiplabs.devpulse.tracking.PasteActionTracker
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DevPulseStartupActivity : ProjectActivity {

    private val logger = thisLogger()

    override suspend fun execute(project: Project) {
        logger.info("DevPulse startup activity executed for project: ${project.name}")

        project.service<DevPulseStatsService>()
        if (service<DevPulseSettingsService>().snapshot().resetStatsOnProjectOpen) {
            project.service<DevPulseStatsService>().resetStatistics()
        }
        project.service<ActiveFileTracker>().start()
        project.service<PasteActionTracker>().start()
        project.service<DevPulseDocumentChangeTracker>().start()
        project.service<DevPulseFocusTracker>().start()
        project.service<DevPulsePomodoroService>()
    }
}
