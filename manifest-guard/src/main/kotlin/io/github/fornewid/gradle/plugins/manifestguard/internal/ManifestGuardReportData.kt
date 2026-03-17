package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.models.ArtifactManifest
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry
import io.github.fornewid.gradle.plugins.manifestguard.models.ModuleManifest

internal data class ManifestGuardReportData(
    val projectPath: String,
    val configurationName: String,
    val dependencies: List<ManifestEntry>,
    val allowedFilter: (dependencyName: String) -> Boolean,
    /**
     * Transform or remove (with null) dependencies in the baseline
     */
    val baselineMap: (dependencyName: String) -> String?,
) {

    private val artifactDeps: List<ArtifactManifest> = dependencies
        .filterIsInstance<ArtifactManifest>()
        .distinct()
        .sortedBy { it.name }

    private val moduleDeps: List<ModuleManifest> = dependencies
        .filterIsInstance<ModuleManifest>()
        .distinct()
        .sortedBy { it.name }

    private fun allDepsReport(artifacts: Boolean, modules: Boolean): String = buildString {
        if (modules) {
            moduleDeps.toReportString().apply {
                if (this.isNotBlank()) {
                    append(this)
                }
            }
        }
        if (artifacts) {
            artifactDeps.toReportString().apply {
                if (this.isNotBlank()) {
                    append(this)
                }
            }
        }
    }

    val disallowed: List<ManifestEntry> = dependencies
        .filter { !allowedFilter(it.name) }
        .distinct()
        .sortedBy { it.name }

    private fun List<ManifestEntry>.toReportString() : String = buildString {
        this@toReportString.forEach {
            baselineMap(it.name)?.let(::appendLine)
        }
    }

    fun reportForConfig(artifacts: Boolean = true, modules: Boolean = true): String {
        return allDepsReport(artifacts, modules)
    }
}
