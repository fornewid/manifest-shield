package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry

internal object TreeContentBuilder {

    fun build(
        entries: List<ManifestEntry>,
        elementType: String,
        sourceMap: Map<String, String>,
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
            val source = sourceMap[key] ?: "unknown"
            val line = baselineMap(entry.toBaselineString()) ?: continue
            grouped.getOrPut(source) { mutableListOf() }.add(line)
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
        sourceMap: Map<String, String>,
        baselineMap: (String) -> String?,
    ): String {
        val manifestLevel = listOf("uses-feature", "uses-permission")
        val applicationLevel = listOf("activity", "service", "receiver", "provider")

        // Group: source → tag → list of entries
        val sourceTagEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        for ((tag, elementType, entries) in categories) {
            for (entry in entries) {
                val key = "$elementType#${entry.name}"
                val source = sourceMap[key] ?: "unknown"
                val line = baselineMap(entry.toBaselineString()) ?: continue
                sourceTagEntries
                    .getOrPut(source) { mutableMapOf() }
                    .getOrPut(tag) { mutableListOf() }
                    .add(line)
            }
        }

        val sortedSources = sourceTagEntries.keys.sorted().let { sources ->
            val app = sources.filter { it == "app" }
            val rest = sources.filter { it != "app" }
            app + rest
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
}
