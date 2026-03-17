package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.models.ArtifactManifest
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry
import io.github.fornewid.gradle.plugins.manifestguard.models.ModuleManifest
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

internal object ManifestVisitor {

    private fun ResolvedDependencyResult.toDep(): ManifestEntry? {
        return when (val componentIdentifier = selected.id) {
            is ProjectComponentIdentifier -> ModuleManifest(
                path = componentIdentifier.projectPath,
            )
            is ModuleComponentIdentifier -> {
                ArtifactManifest(
                    group = componentIdentifier.group,
                    artifact = componentIdentifier.module,
                    version = componentIdentifier.version,
                )
            }
            else -> {
                null
            }
        }
    }

    private fun visit(
        reportData: MutableList<ManifestEntry>,
        resolvedDependencyResults: Collection<ResolvedDependencyResult>
    ) {
        for (resolvedDependencyResult: ResolvedDependencyResult in resolvedDependencyResults) {
            resolvedDependencyResult.toDep()
                ?.let { dep: ManifestEntry ->
                    if (!reportData.contains(dep)) {
                        reportData.add(dep)
                        visit(
                            reportData = reportData,
                            resolvedDependencyResults = resolvedDependencyResult
                                .selected
                                .dependencies
                                .filterIsInstance<ResolvedDependencyResult>()
                        )
                    }
                }
        }
    }

    fun traverseComponentDependencies(resolvedComponentResult: ResolvedComponentResult): List<ManifestEntry> {
        val firstLevelDependencies = resolvedComponentResult
            .dependencies
            .filterIsInstance<ResolvedDependencyResult>()

        val entries = mutableListOf<ManifestEntry>()
        visit(entries, firstLevelDependencies)
        return entries
    }
}
