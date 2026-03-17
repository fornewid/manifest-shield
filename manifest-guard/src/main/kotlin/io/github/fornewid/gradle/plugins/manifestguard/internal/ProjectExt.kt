package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.Provider

internal fun Project.isRootProject(): Boolean = this == rootProject

internal fun getQualifiedBaselineTaskForProjectPath(path: String): String {
    val separator = if (path == ":") "" else ":"
    return "${path}${separator}${ManifestGuardPlugin.MANIFEST_GUARD_BASELINE_TASK_NAME}"
}

internal val Project.projectConfigurations: ConfigurationContainer
    get() = if (isRootProject()) {
        buildscript.configurations
    } else {
        configurations
    }

internal fun ConfigurationContainer.getResolvedComponentResult(name: String): Provider<ResolvedComponentResult>? = this
    .findByName(name)
    ?.incoming
    ?.resolutionResult
    ?.rootComponent
