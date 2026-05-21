/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse.model

import java.time.Instant

data class EditEvent(
    val timestamp: Instant,
    val file: String,
    val type: EditType,
    val characterCount: Int
)