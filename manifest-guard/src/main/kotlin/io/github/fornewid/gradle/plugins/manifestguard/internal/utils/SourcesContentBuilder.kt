package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import io.github.fornewid.gradle.plugins.manifestguard.internal.EnabledCategories
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry

internal object SourcesContentBuilder {

    fun build(
        entries: List<ManifestEntry>,
        elementType: String,
        sourceMap: Map<String, List<String>>,
        baselineMap: (String) -> String?,
    ): String {
        if (sourceMap.isEmpty()) {
            return entries
                .mapNotNull { entry -> baselineMap(entry.toBaselineString())?.let { "$it -- unknown" } }
                .joinToString("\n", postfix = if (entries.isNotEmpty()) "\n" else "")
        }

        val grouped = mutableMapOf<String, MutableList<String>>()
        for (entry in entries) {
            val key = "$elementType#${entry.name}"
            val sources = sourceMap[key] ?: listOf("unknown")
            val line = baselineMap(entry.toBaselineString()) ?: continue
            for (source in sources) {
                grouped.getOrPut(source) { mutableListOf() }.add(line)
            }
        }

        return buildString {
            val sortedSources = grouped.keys.sorted().let { sources ->
                val app = sources.filter { it == "app" }
                val rest = sources.filter { it != "app" }
                app + rest
            }
            for (source in sortedSources) {
                appendLine("$source:")
                grouped[source]?.sorted()?.forEach { line ->
                    appendLine("  $line")
                }
            }
        }
    }

    /**
     * Build merged tree content grouped by source, then by manifest/application level categories.
     */
    fun buildMerged(
        categories: List<Triple<String, String, List<ManifestEntry>>>,
        sourceMap: Map<String, List<String>>,
        baselineMap: (String) -> String?,
    ): String {
        val manifestLevel = listOf("uses-feature", "uses-permission", "permission")
        val applicationLevel = listOf("activity", "activity-alias", "service", "receiver", "provider")

        // Group: source → tag → list of entries
        val sourceTagEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        for ((tag, elementType, entries) in categories) {
            for (entry in entries) {
                val key = "$elementType#${entry.name}"
                val sources = sourceMap[key] ?: listOf("unknown")
                val line = baselineMap(entry.toBaselineString()) ?: continue
                for (source in sources) {
                    sourceTagEntries
                        .getOrPut(source) { mutableMapOf() }
                        .getOrPut(tag) { mutableListOf() }
                        .add(line)
                }
            }
        }

        val sortedSources = sourceTagEntries.keys.sorted().let { sources ->
            val local = sources.filter { it.startsWith(":") }.sorted()
            val external = sources.filter { !it.startsWith(":") }.sorted()
            local + external
        }

        return buildString {
            for ((sourceIdx, source) in sortedSources.withIndex()) {
                appendLine("[$source]")
                val tagMap = sourceTagEntries[source] ?: continue

                val manifestTags = categories.map { it.first }.filter { it in manifestLevel && it in tagMap }
                val appTags = categories.map { it.first }.filter { it in applicationLevel && it in tagMap }

                if (manifestTags.isNotEmpty()) {
                    appendLine("<manifest>")
                    for ((i, tag) in manifestTags.withIndex()) {
                        appendLine("$tag:")
                        tagMap[tag]?.sorted()?.forEach { appendLine("  $it") }
                        if (i < manifestTags.size - 1) appendLine()
                    }
                }

                if (manifestTags.isNotEmpty() && appTags.isNotEmpty()) {
                    appendLine()
                }

                if (appTags.isNotEmpty()) {
                    appendLine("<application>")
                    for ((i, tag) in appTags.withIndex()) {
                        appendLine("$tag:")
                        tagMap[tag]?.sorted()?.forEach { appendLine("  $it") }
                        if (i < appTags.size - 1) appendLine()
                    }
                }

                if (sourceIdx < sortedSources.size - 1) {
                    appendLine()
                }
            }
        }
    }

    /**
     * Build merged tree content with full feature parity with ManifestGuardListTask output.
     * Includes uses-sdk, intent-filters, and startup initializers.
     */
    fun buildMergedWithSdk(
        manifest: ManifestExtraction,
        sourceMap: Map<String, List<String>>,
        baselineMap: (String) -> String?,
        projectPath: String,
        flags: EnabledCategories,
    ): String {
        val applicationLevel = listOf("activity", "activity-alias", "meta-data", "service", "receiver",
            "profileable", "provider", "uses-library", "uses-native-library", "androidx.startup")

        val guardIntentFilters = flags.intentFilters

        // Group: source → tag → list of lines
        val sourceTagEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        fun addEntries(tag: String, elementType: String, entries: List<ManifestEntry>, isComponent: Boolean = false) {
            for (entry in entries) {
                val key = "$elementType#${entry.name}"
                val entrySources = sourceMap[key] ?: listOf("unknown")
                val line = baselineMap(entry.toBaselineString()) ?: continue
                for (source in entrySources) {
                    val lines = sourceTagEntries
                        .getOrPut(source) { mutableMapOf() }
                        .getOrPut(tag) { mutableListOf() }
                    lines.add(line)
                    if (isComponent && guardIntentFilters && entry is ManifestComponent && entry.intentFilters.isNotEmpty()) {
                        for (filter in entry.intentFilters) {
                            lines.add("  intent-filter:")
                            filter.actions.forEach { lines.add("    action: $it") }
                            filter.categories.forEach { lines.add("    category: $it") }
                            filter.dataSpecs.forEach { lines.add("    data: $it") }
                        }
                    }
                }
            }
        }

        if (flags.features && manifest.features.isNotEmpty()) addEntries("uses-feature", "uses-feature", manifest.features)
        if (flags.permissions && manifest.permissions.isNotEmpty()) addEntries("uses-permission", "uses-permission", manifest.permissions)
        if (flags.permissionsSdk23 && manifest.permissionsSdk23.isNotEmpty()) addEntries("uses-permission-sdk-23", "uses-permission-sdk-23", manifest.permissionsSdk23)
        if (flags.permissionDeclarations && manifest.permissionDeclarations.isNotEmpty()) addEntries("permission", "permission", manifest.permissionDeclarations)
        if (flags.supportsGlTexture && manifest.supportsGlTextures.isNotEmpty()) addEntries("supports-gl-texture", "supports-gl-texture", manifest.supportsGlTextures)
        if (flags.activities && manifest.activities.isNotEmpty()) addEntries("activity", "activity", manifest.activities, isComponent = true)
        if (flags.activityAliases && manifest.activityAliases.isNotEmpty()) addEntries("activity-alias", "activity-alias", manifest.activityAliases, isComponent = true)
        if (flags.metaData && manifest.metaData.isNotEmpty()) addEntries("meta-data", "meta-data", manifest.metaData)
        if (flags.services && manifest.services.isNotEmpty()) addEntries("service", "service", manifest.services, isComponent = true)
        if (flags.receivers && manifest.receivers.isNotEmpty()) addEntries("receiver", "receiver", manifest.receivers, isComponent = true)
        if (flags.providers && manifest.providers.isNotEmpty()) addEntries("provider", "provider", manifest.providers, isComponent = true)
        if (flags.usesLibrary && manifest.usesLibraries.isNotEmpty()) addEntries("uses-library", "uses-library", manifest.usesLibraries)
        if (flags.usesNativeLibrary && manifest.usesNativeLibraries.isNotEmpty()) addEntries("uses-native-library", "uses-native-library", manifest.usesNativeLibraries)

        // Non-ManifestEntry elements (attributed to the current project module)
        fun addProjectLines(tag: String, lines: List<String>) {
            if (lines.isNotEmpty()) {
                sourceTagEntries
                    .getOrPut(projectPath) { mutableMapOf() }
                    .getOrPut(tag) { mutableListOf() }
                    .addAll(lines)
            }
        }
        if (flags.supportsScreens && manifest.supportsScreens != null) {
            addProjectLines("supports-screens", manifest.supportsScreens!!.toBaselineLines())
        }
        if (flags.compatibleScreens && manifest.compatibleScreens.isNotEmpty()) {
            addProjectLines("compatible-screens", manifest.compatibleScreens)
        }
        if (flags.usesConfiguration && manifest.usesConfiguration != null) {
            addProjectLines("uses-configuration", manifest.usesConfiguration!!.toBaselineLines())
        }
        if (flags.queries && manifest.queries != null) {
            addProjectLines("queries", manifest.queries!!.toBaselineLines())
        }
        if (flags.profileable && manifest.profileable != null) {
            addProjectLines("profileable", manifest.profileable!!.toBaselineLines())
        }

        // Startup initializers (attributed to the current project module)
        if (flags.startup && manifest.startupInitializers.isNotEmpty()) {
            sourceTagEntries
                .getOrPut(projectPath) { mutableMapOf() }
                .getOrPut("androidx.startup") { mutableListOf() }
                .addAll(manifest.startupInitializers)
        }

        // uses-sdk is always from the current project module
        val sdk = manifest.sdk
        if (flags.sdk && sdk != null) {
            sourceTagEntries.getOrPut(projectPath) { mutableMapOf() }
        }

        val sortedSources = sourceTagEntries.keys.sorted().let { sources ->
            val local = sources.filter { it.startsWith(":") }.sorted()
            val external = sources.filter { !it.startsWith(":") }.sorted()
            local + external
        }

        return buildString {
            for ((sourceIdx, source) in sortedSources.withIndex()) {
                appendLine("[$source]")
                val tagMap = sourceTagEntries[source] ?: continue

                // uses-sdk is per-source "app" only (it comes from the build config)
                val hasSdk = flags.sdk && source == projectPath && manifest.sdk != null

                val manifestTags = mutableListOf<String>()
                if (hasSdk) manifestTags.add("uses-sdk")
                manifestTags.addAll(listOf("uses-feature", "uses-permission", "uses-permission-sdk-23", "permission",
                    "supports-screens", "compatible-screens", "uses-configuration", "supports-gl-texture", "queries").filter { it in tagMap })

                val appTags = applicationLevel.filter { it in tagMap }

                if (manifestTags.isNotEmpty()) {
                    appendLine("<manifest>")
                    for ((i, tag) in manifestTags.withIndex()) {
                        if (tag == "uses-sdk") {
                            appendLine("uses-sdk:")
                            manifest.sdk?.minSdkVersion?.let { appendLine("  minSdkVersion=$it") }
                            manifest.sdk?.targetSdkVersion?.let { appendLine("  targetSdkVersion=$it") }
                        } else {
                            appendLine("$tag:")
                            tagMap[tag]?.forEach { appendLine("  $it") }
                        }
                        if (i < manifestTags.size - 1) appendLine()
                    }
                }

                if (manifestTags.isNotEmpty() && appTags.isNotEmpty()) {
                    appendLine()
                }

                if (appTags.isNotEmpty()) {
                    appendLine("<application>")
                    for ((i, tag) in appTags.withIndex()) {
                        appendLine("$tag:")
                        tagMap[tag]?.forEach { appendLine("  $it") }
                        if (i < appTags.size - 1) appendLine()
                    }
                }

                if (sourceIdx < sortedSources.size - 1) {
                    appendLine()
                }
            }
        }
    }
}
