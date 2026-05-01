package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

import io.github.fornewid.gradle.plugins.manifestshield.internal.EnabledCategories
import io.github.fornewid.gradle.plugins.manifestshield.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.manifestshield.internal.STARTUP_PROVIDER_NAME
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestEntry
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestQuery

internal object SourcesContentBuilder {

    /** Group label for elements whose source the parser could not resolve. */
    const val UNRESOLVED_SOURCE: String = "<unresolved>"

    fun build(
        entries: List<ManifestEntry>,
        elementType: String,
        sourceMap: Map<String, List<String>>,
    ): String {
        if (sourceMap.isEmpty()) {
            return entries
                .map { entry -> "${entry.toBaselineString()} -- unknown" }
                .joinToString("\n", postfix = if (entries.isNotEmpty()) "\n" else "")
        }

        val grouped = mutableMapOf<String, MutableList<String>>()
        for (entry in entries) {
            val key = "$elementType#${entry.name}"
            val sources = sourceMap[key] ?: listOf("unknown")
            val line = entry.toBaselineString()
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
     * Build merged tree content with full feature parity with ManifestShieldListTask output.
     * Includes uses-sdk, intent-filters, and startup initializers.
     */
    fun buildMergedWithSdk(
        manifest: ManifestExtraction,
        sourceMap: Map<String, List<String>>,
        projectPath: String,
        flags: EnabledCategories,
    ): String {
        val applicationLevel = listOf("activity", "activity-alias", "meta-data", "service", "receiver",
            "profileable", "provider", "uses-library", "uses-native-library", "androidx.startup")

        val guardIntentFilter = flags.intentFilter

        // Group: source → tag → list of lines
        val sourceTagEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        fun addEntries(tag: String, elementType: String, entries: List<ManifestEntry>, isComponent: Boolean = false) {
            for (entry in entries) {
                val key = "$elementType#${entry.name}"
                val entrySources = sourceMap[key] ?: listOf("unknown")
                val line = entry.toBaselineString()
                for (source in entrySources) {
                    val lines = sourceTagEntries
                        .getOrPut(source) { mutableMapOf() }
                        .getOrPut(tag) { mutableListOf() }
                    lines.add(line)
                    if (isComponent && entry is ManifestComponent) {
                        for (permLine in entry.permissionLines()) {
                            lines.add("  $permLine")
                        }
                    }
                    if (isComponent && guardIntentFilter && entry is ManifestComponent && entry.intentFilter.isNotEmpty()) {
                        for (filter in entry.intentFilter) {
                            lines.add("  intent-filter:")
                            filter.actions.forEach { lines.add("    action: $it") }
                            filter.categories.forEach { lines.add("    category: $it") }
                            filter.dataSpecs.forEach { lines.add("    data: $it") }
                        }
                    }
                }
            }
        }

        fun <T : ManifestEntry> filterRequired(entries: List<T>, isRequired: (T) -> Boolean): List<T> =
            if (flags.requiredOnly) entries.filter(isRequired) else entries

        val featureList = filterRequired(manifest.usesFeature) { it.required }
        if (flags.usesFeature && featureList.isNotEmpty()) addEntries("uses-feature", "uses-feature", featureList)
        if (flags.usesPermission && manifest.usesPermission.isNotEmpty()) addEntries("uses-permission", "uses-permission", manifest.usesPermission)
        if (flags.usesPermissionSdk23 && manifest.usesPermissionSdk23.isNotEmpty()) addEntries("uses-permission-sdk-23", "uses-permission-sdk-23", manifest.usesPermissionSdk23)
        if (flags.permission && manifest.permission.isNotEmpty()) addEntries("permission", "permission", manifest.permission)
        if (flags.supportsGlTexture && manifest.supportsGlTextures.isNotEmpty()) addEntries("supports-gl-texture", "supports-gl-texture", manifest.supportsGlTextures)
        fun filterComponents(components: List<ManifestComponent>): List<ManifestComponent> =
            components
                .filter { !flags.exportedOnly || it.exported == true }
                .filter { !flags.unprotectedOnly || !it.hasPermissionProtection() }

        if (flags.activity) filterComponents(manifest.activity).let { if (it.isNotEmpty()) addEntries("activity", "activity", it, isComponent = true) }
        if (flags.activityAlias) filterComponents(manifest.activityAlias).let { if (it.isNotEmpty()) addEntries("activity-alias", "activity-alias", it, isComponent = true) }
        if (flags.metaData && manifest.metaData.isNotEmpty()) addEntries("meta-data", "meta-data", manifest.metaData)
        if (flags.service) filterComponents(manifest.service).let { if (it.isNotEmpty()) addEntries("service", "service", it, isComponent = true) }
        if (flags.receiver) filterComponents(manifest.receiver).let { if (it.isNotEmpty()) addEntries("receiver", "receiver", it, isComponent = true) }
        if (flags.provider) {
            val providers = if (flags.startup) {
                manifest.provider.filter { it.name != STARTUP_PROVIDER_NAME }
            } else {
                manifest.provider
            }
            filterComponents(providers).let { if (it.isNotEmpty()) addEntries("provider", "provider", it, isComponent = true) }
        }
        val libList = filterRequired(manifest.usesLibraries) { it.required }
        if (flags.usesLibrary && libList.isNotEmpty()) addEntries("uses-library", "uses-library", libList)
        if (flags.usesNativeLibrary && manifest.usesNativeLibraries.isNotEmpty()) addEntries("uses-native-library", "uses-native-library", manifest.usesNativeLibraries)

        // Singleton elements have no android:name, so the blame log keys them by type alone.
        // We resolve their source via sourceMap[elementType] instead of attributing them all
        // to the current project. If parsing ever misses a source, the entry surfaces under
        // the [<unresolved>] group rather than being silently misattributed to :app.
        fun addSingletonEntries(tag: String, elementType: String, lines: List<String>) {
            if (lines.isEmpty()) return
            val sources = sourceMap[elementType] ?: listOf(UNRESOLVED_SOURCE)
            for (source in sources) {
                sourceTagEntries
                    .getOrPut(source) { mutableMapOf() }
                    .getOrPut(tag) { mutableListOf() }
                    .addAll(lines)
            }
        }
        if (flags.supportsScreens && manifest.supportsScreens != null) {
            addSingletonEntries("supports-screens", "supports-screens", manifest.supportsScreens.toBaselineLines())
        }
        if (flags.compatibleScreens && manifest.compatibleScreens.isNotEmpty()) {
            addSingletonEntries("compatible-screens", "compatible-screens", manifest.compatibleScreens)
        }
        if (flags.usesConfiguration && manifest.usesConfiguration != null) {
            addSingletonEntries("uses-configuration", "uses-configuration", manifest.usesConfiguration.toBaselineLines())
        }
        if (flags.queries && manifest.queries != null) {
            addQueriesEntries(manifest.queries, sourceMap, sourceTagEntries)
        }
        if (flags.profileable && manifest.profileable != null) {
            addSingletonEntries("profileable", "profileable", manifest.profileable.toBaselineLines())
        }

        // Startup initializers are <meta-data> children of androidx.startup's InitializationProvider,
        // declared by libraries that hook into App Startup. They are not directly traceable through
        // the merged blame log entries, so they remain attributed to the current project module.
        if (flags.startup && manifest.startupInitializers.isNotEmpty()) {
            sourceTagEntries
                .getOrPut(projectPath) { mutableMapOf() }
                .getOrPut("androidx.startup") { mutableListOf() }
                .addAll(manifest.startupInitializers)
        }

        // uses-sdk: AGP injects this from build.gradle's minSdk/targetSdk, which the blame log
        // records as `INJECTED from <app>/AndroidManifest.xml`, so it resolves to the project
        // path naturally via sourceMap.
        if (flags.usesSdk && manifest.usesSdk != null) {
            addSingletonEntries("uses-sdk", "uses-sdk", manifest.usesSdk.toBaselineLines())
        }

        // Group order: local modules (`:`-prefixed), then external libraries, then <unresolved>
        // last so reviewers see it as an exception rather than a normal source.
        val sortedSources = sourceTagEntries.keys.let { sources ->
            val local = sources.filter { it.startsWith(":") }.sorted()
            val unresolved = sources.filter { it == UNRESOLVED_SOURCE }
            val external = (sources - local.toSet() - unresolved.toSet()).sorted()
            local + external + unresolved
        }

        val manifestLevelOrder = listOf(
            "uses-sdk", "uses-feature", "uses-permission", "uses-permission-sdk-23", "permission",
            "supports-screens", "compatible-screens", "uses-configuration", "supports-gl-texture", "queries"
        )

        return buildString {
            for ((sourceIdx, source) in sortedSources.withIndex()) {
                appendLine("[$source]")
                val tagMap = sourceTagEntries[source] ?: continue

                val allTags = manifestLevelOrder.filter { it in tagMap } +
                    applicationLevel.filter { it in tagMap }

                for ((i, tag) in allTags.withIndex()) {
                    appendLine("$tag:")
                    tagMap[tag]?.forEach { appendLine("  $it") }
                    if (i < allTags.size - 1) appendLine()
                }

                if (sourceIdx < sortedSources.size - 1) {
                    appendLine()
                }
            }
        }
    }

    /**
     * Attribute the contents of a `<queries>` block. Unlike other singleton elements,
     * `<queries>` is a container whose children (`<package>`, `<provider>`, `<intent>`)
     * each carry independent sources in the manifest-merger blame log.
     *
     * - `<package>`: keyed by `package#$name` in the blame log → resolved per package.
     * - `<provider>` and `<intent>`: child-level keys in the blame log are inconsistent
     *   (providers may use authorities, intents are composite), so they are attributed
     *   to the first source of the enclosing `<queries>` container as a best-effort.
     *   Per-child resolution for these two is tracked as a follow-up.
     */
    private fun addQueriesEntries(
        queries: ManifestQuery,
        sourceMap: Map<String, List<String>>,
        sourceTagEntries: MutableMap<String, MutableMap<String, MutableList<String>>>,
    ) {
        fun addLine(source: String, line: String) {
            sourceTagEntries
                .getOrPut(source) { mutableMapOf() }
                .getOrPut("queries") { mutableListOf() }
                .add(line)
        }

        queries.packages.sorted().forEach { name ->
            val sources = sourceMap["package#$name"] ?: listOf(UNRESOLVED_SOURCE)
            for (source in sources) {
                addLine(source, "package: $name")
            }
        }

        if (queries.providers.isNotEmpty() || queries.intents.isNotEmpty()) {
            val containerSource = sourceMap["queries"]?.firstOrNull() ?: UNRESOLVED_SOURCE
            queries.providers.sorted().forEach { auth ->
                addLine(containerSource, "provider: $auth")
            }
            queries.intents.forEach { intent ->
                addLine(containerSource, "intent:")
                intent.actions.forEach { addLine(containerSource, "  action: $it") }
                intent.categories.forEach { addLine(containerSource, "  category: $it") }
                intent.dataSpecs.forEach { addLine(containerSource, "  data: $it") }
            }
        }
    }
}
