/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */

package com.github.filiplabs.devpulse.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.filiplabs.devpulse.MyBundle

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        thisLogger().info(MyBundle["projectService", project.name])
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..100).random()
}
