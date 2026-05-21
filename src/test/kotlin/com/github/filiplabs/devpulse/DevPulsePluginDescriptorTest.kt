/*
 * Copyright (c) 2026 Filip Cvetković / filip-labs.
 * All rights reserved.
 *
 * This source code is source-available for viewing only.
 * Use, copying, modification, distribution, or commercial use is prohibited
 * without explicit written permission and a paid license from the author.
 */
package com.github.filiplabs.devpulse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevPulsePluginDescriptorTest {

    @Test
    fun pluginXmlUsesDevPulseRegistrations() {
        val pluginXml = javaClass.classLoader
            .getResourceAsStream("META-INF/plugin.xml")
            ?.bufferedReader()
            ?.use { it.readText() }

        assertNotNull("plugin.xml should be available on the test classpath", pluginXml)

        val xml = pluginXml!!

        assertTrue(
            "plugin.xml should register DevPulse tool window factory",
            xml.contains(
                "factoryClass=\"com.github.filiplabs.devpulse.toolWindow.DevPulseToolWindowFactory\""
            )
        )

        assertTrue(
            "plugin.xml should register DevPulse startup activity",
            xml.contains(
                "implementation=\"com.github.filiplabs.devpulse.startup.DevPulseStartupActivity\""
            )
        )

        assertFalse("plugin.xml should not reference MyProjectActivity", xml.contains("MyProjectActivity"))
        assertFalse("plugin.xml should not reference MyToolWindowFactory", xml.contains("MyToolWindowFactory"))
        assertFalse("plugin.xml should not reference MyProjectService", xml.contains("MyProjectService"))

        Class.forName("com.github.filiplabs.devpulse.toolWindow.DevPulseToolWindowFactory")
        Class.forName("com.github.filiplabs.devpulse.startup.DevPulseStartupActivity")
    }
}