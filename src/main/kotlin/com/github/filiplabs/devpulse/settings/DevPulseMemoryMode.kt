/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.settings

enum class DevPulseMemoryMode(
    private val label: String
) {
    KEEP_UNTIL_IDE_CLOSE("Keep until IntelliJ closes"),
    KEEP_UNTIL_MANUAL_RESET("Keep until manual reset"),
    RESET_WHEN_TOOL_WINDOW_OPENS("Reset when Tool Window opens");

    override fun toString(): String = label
}
