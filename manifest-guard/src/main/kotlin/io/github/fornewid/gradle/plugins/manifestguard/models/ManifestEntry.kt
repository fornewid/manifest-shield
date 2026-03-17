package io.github.fornewid.gradle.plugins.manifestguard.models

internal interface ManifestEntry {
    val name: String
    fun toBaselineString(): String = name
}
