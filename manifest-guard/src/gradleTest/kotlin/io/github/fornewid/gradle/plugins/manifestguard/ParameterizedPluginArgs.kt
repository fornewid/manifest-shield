package io.github.fornewid.gradle.plugins.manifestguard

import org.gradle.util.GradleVersion

data class ParameterizedPluginArgs(
    val gradleVersion: GradleVersion,
    val withConfigurationCache: Boolean,
)