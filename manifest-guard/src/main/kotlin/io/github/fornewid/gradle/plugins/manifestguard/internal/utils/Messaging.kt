package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin.Companion.MANIFEST_GUARD_BASELINE_TASK_NAME
import io.github.fornewid.gradle.plugins.manifestguard.internal.getQualifiedBaselineTaskForProjectPath

internal object Messaging {
    const val dependencyChangeDetected = "***** DEPENDENCY CHANGE DETECTED *****"

    fun rebaselineMessage(projectPath: String): String = """
        If this is intentional, re-baseline using ./gradlew ${getQualifiedBaselineTaskForProjectPath(projectPath)}
        Or use ./gradlew $MANIFEST_GUARD_BASELINE_TASK_NAME to re-baseline dependencies in entire project.
    """.trimIndent()
}