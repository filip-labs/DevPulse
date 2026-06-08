/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class DevPulseProjectLifecycleService : Disposable {

    override fun dispose() {
        // This service acts as DevPulse's project-level parent disposable.
    }
}
