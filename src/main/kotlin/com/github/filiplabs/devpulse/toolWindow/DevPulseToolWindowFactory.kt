/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */

package com.github.filiplabs.devpulse.toolWindow

import com.github.filiplabs.devpulse.settings.DevPulseMemoryMode
import com.github.filiplabs.devpulse.settings.DevPulseSettingsService
import com.github.filiplabs.devpulse.storage.DevPulseStatsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class DevPulseToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (service<DevPulseSettingsService>().snapshot().memoryMode == DevPulseMemoryMode.RESET_WHEN_TOOL_WINDOW_OPENS) {
            project.service<DevPulseStatsService>().resetStatistics()
        }

        val panel = DevPulseDashboardPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
