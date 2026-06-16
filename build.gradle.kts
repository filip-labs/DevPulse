import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.api.tasks.Delete

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

val intellijIdeaVersion = providers.gradleProperty("intellijIdeaVersion").get()
val junitVersion = providers.gradleProperty("junitVersion").get()
val composeMultiplatformVersion = providers.gradleProperty("composeMultiplatformVersion").get()

sourceSets {
    create("composePreview") {
        java.srcDir("src/composePreview/kotlin")
        compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

dependencies {
    compileOnly("org.jetbrains.compose.runtime:runtime:$composeMultiplatformVersion")
    testCompileOnly("org.jetbrains.compose.runtime:runtime:$composeMultiplatformVersion")
    testImplementation("junit:junit:$junitVersion")

    "composePreviewImplementation"(sourceSets.main.get().output)
    "composePreviewImplementation"(compose.desktop.currentOs)
    "composePreviewImplementation"("org.jetbrains.compose.ui:ui-tooling-preview:$composeMultiplatformVersion")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(intellijIdeaVersion)
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

tasks.named("check") {
    dependsOn("compileComposePreviewKotlin")
}
