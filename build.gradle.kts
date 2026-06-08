import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.Delete

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}
// The IntelliJ Platform Gradle plugin can keep stale instrumented test outputs.
// Clean this directory before instrumentation so removed tests are not restored.
val cleanInstrumentedTestCode by tasks.registering(Delete::class) {
    description = ""
    delete(layout.buildDirectory.dir("instrumented/instrumentTestCode"))
}

tasks.named("instrumentTestCode") {
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    dependsOn(cleanInstrumentedTestCode)
}
