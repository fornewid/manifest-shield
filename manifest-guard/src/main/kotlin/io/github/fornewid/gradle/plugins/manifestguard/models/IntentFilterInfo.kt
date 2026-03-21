package io.github.fornewid.gradle.plugins.manifestguard.models

internal data class IntentFilterInfo(
    val actions: List<String>,
    val categories: List<String>,
    val dataSpecs: List<String>,
)
