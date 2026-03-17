package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin.Companion.MANIFEST_GUARD_BASELINE_TASK_NAME

internal object Messaging {

    fun rebaselineMessage(projectPath: String, configurationName: String): String {
        val separator = if (projectPath == ":") "" else ":"
        return """
            If this is intentional, re-baseline using ./gradlew ${projectPath}${separator}manifestGuardBaseline${configurationName.capitalize()}
            Or use ./gradlew $MANIFEST_GUARD_BASELINE_TASK_NAME to re-baseline in entire project.
        """.trimIndent()
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }
}
