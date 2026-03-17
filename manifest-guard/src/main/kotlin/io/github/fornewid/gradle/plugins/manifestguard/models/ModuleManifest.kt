package io.github.fornewid.gradle.plugins.manifestguard.models

internal data class ModuleManifest(
    val path: String,
) : ManifestEntry {
    override val name: String get() = path
}
