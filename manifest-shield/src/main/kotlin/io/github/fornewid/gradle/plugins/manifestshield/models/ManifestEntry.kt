package io.github.fornewid.gradle.plugins.manifestshield.models

internal interface ManifestEntry {
    val name: String
    fun toBaselineString(): String = name
}
