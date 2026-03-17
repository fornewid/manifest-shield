package io.github.fornewid.gradle.plugins.manifestguard.models

internal data class ArtifactManifest(
    val group: String,
    val artifact: String,
    val version: String,
) : ManifestEntry {
    override val name: String get() = "$group:$artifact:$version"
}
