package io.github.fornewid.gradle.plugins.manifestguard.internal.list

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import io.github.fornewid.gradle.plugins.manifestguard.internal.GuardFlags
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestVisitor
import io.github.fornewid.gradle.plugins.manifestguard.internal.applyConfig
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.BaselineCreated
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.HasDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.NoDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Messaging
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Tasks.declareCompatibilities
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

internal abstract class ManifestGuardListTask : DefaultTask(), GuardFlags {

    init {
        group = ManifestGuardPlugin.MANIFEST_GUARD_TASK_GROUP
    }

    @get:InputFile
    abstract val mergedManifestFile: RegularFileProperty

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val shouldBaseline: Property<Boolean>

    abstract override val guardSdk: Property<Boolean>
    abstract override val guardPermissions: Property<Boolean>
    abstract override val guardPermissionsSdk23: Property<Boolean>
    abstract override val guardPermissionDeclarations: Property<Boolean>
    abstract override val guardActivities: Property<Boolean>
    abstract override val guardActivityAliases: Property<Boolean>
    abstract override val guardServices: Property<Boolean>
    abstract override val guardReceivers: Property<Boolean>
    abstract override val guardProviders: Property<Boolean>
    abstract override val guardFeatures: Property<Boolean>
    abstract override val guardIntentFilters: Property<Boolean>
    abstract override val guardStartup: Property<Boolean>
    abstract override val guardSupportsScreens: Property<Boolean>
    abstract override val guardCompatibleScreens: Property<Boolean>
    abstract override val guardUsesConfiguration: Property<Boolean>
    abstract override val guardSupportsGlTexture: Property<Boolean>
    abstract override val guardQueries: Property<Boolean>
    abstract override val guardMetaData: Property<Boolean>
    abstract override val guardUsesLibrary: Property<Boolean>
    abstract override val guardUsesNativeLibrary: Property<Boolean>
    abstract override val guardProfileable: Property<Boolean>

    @get:OutputDirectory
    abstract val baselineDir: DirectoryProperty

    @get:Input
    abstract val filePrefix: Property<String>

    @get:Input
    abstract val allowedFilter: Property<(String) -> Boolean>

    @get:Input
    abstract val baselineMap: Property<(String) -> String?>

    @TaskAction
    internal fun execute() {
        val manifest = ManifestVisitor.parse(mergedManifestFile.get().asFile)
        val configName = configurationName.get()
        val path = projectPath.get()
        val filter = allowedFilter.get()
        val mapper = baselineMap.get()
        val baseline = shouldBaseline.get()
        val dir = baselineDir.get()
        val prefix = filePrefix.get()
        val showIntentFilters = guardIntentFilters.get()

        // Check for disallowed entries across all entry categories
        val entryCategories = collectEntryCategories(manifest)
        for ((category, entries) in entryCategories) {
            val disallowed = entries.filter { !filter(it.name) }
            if (disallowed.isNotEmpty()) {
                throw GradleException(buildString {
                    appendLine("Disallowed manifest entries found in $path ($configName/$category):")
                    disallowed.forEach { appendLine("  \"${it.name}\"") }
                    appendLine()
                    appendLine("These entries must be removed based on the configured 'allowedFilter'.")
                })
            }
        }

        val reportContent = buildMergedContent(manifest, mapper, showIntentFilters)

        val baselineFile = OutputFileUtils.baselineFile(dir, prefix)

        val result = if (baseline || !baselineFile.exists()) {
            baselineFile.writeText(reportContent)
            BaselineCreated(projectPath = path, configurationName = configName, category = prefix, baselineFile = baselineFile)
        } else {
            ManifestListDiff.performDiff(
                projectPath = path,
                configurationName = configName,
                category = prefix,
                expectedContent = baselineFile.readText(),
                actualContent = reportContent,
            )
        }

        when (result) {
            is HasDiff -> {
                val rebaselineMsg = Messaging.rebaselineMessage(path, configName)
                logger.error(result.createDiffMessage(withColor = true, rebaselineMessage = rebaselineMsg))
                throw GradleException(result.createDiffMessage(withColor = false, rebaselineMessage = rebaselineMsg))
            }
            is NoDiff -> logger.debug(result.noDiffMessage)
            is BaselineCreated -> logger.lifecycle(result.baselineCreatedMessage(withColor = true))
        }
    }

    private fun collectEntryCategories(manifest: ManifestExtraction): List<Pair<String, List<ManifestEntry>>> {
        val entries = mutableListOf<Pair<String, List<ManifestEntry>>>()
        if (guardFeatures.get()) entries.add("uses-feature" to manifest.features)
        if (guardPermissions.get()) entries.add("uses-permission" to manifest.permissions)
        if (guardPermissionDeclarations.get()) entries.add("permission" to manifest.permissionDeclarations)
        if (guardActivities.get()) entries.add("activity" to manifest.activities)
        if (guardActivityAliases.get()) entries.add("activity-alias" to manifest.activityAliases)
        if (guardServices.get()) entries.add("service" to manifest.services)
        if (guardReceivers.get()) entries.add("receiver" to manifest.receivers)
        if (guardProviders.get()) entries.add("provider" to manifest.providers)
        return entries
    }

    private fun buildMergedContent(
        manifest: ManifestExtraction,
        baselineMap: (String) -> String?,
        showIntentFilters: Boolean,
    ): String = buildString {
        val manifestLevel = listOf("uses-sdk", "uses-feature", "uses-permission", "uses-permission-sdk-23", "permission",
            "supports-screens", "compatible-screens", "uses-configuration", "supports-gl-texture", "queries")
        val applicationLevel = listOf("activity", "activity-alias", "meta-data", "service", "receiver",
            "profileable", "provider", "uses-library", "uses-native-library", "androidx.startup")

        // Collect all categories with their entries
        data class Section(val tag: String, val lines: List<String>)

        val sections = mutableListOf<Section>()

        // uses-sdk (special: not ManifestEntry based)
        val sdk = manifest.sdk
        if (guardSdk.get() && sdk != null) {
            val sdkLines = mutableListOf<String>()
            sdk.minSdkVersion?.let { sdkLines.add("minSdkVersion=$it") }
            sdk.targetSdkVersion?.let { sdkLines.add("targetSdkVersion=$it") }
            if (sdkLines.isNotEmpty()) sections.add(Section("uses-sdk", sdkLines))
        }

        // Entry-based manifest-level categories
        if (guardFeatures.get() && manifest.features.isNotEmpty()) {
            sections.add(Section("uses-feature", manifest.features.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardPermissions.get() && manifest.permissions.isNotEmpty()) {
            sections.add(Section("uses-permission", manifest.permissions.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardPermissionsSdk23.get() && manifest.permissionsSdk23.isNotEmpty()) {
            sections.add(Section("uses-permission-sdk-23", manifest.permissionsSdk23.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardPermissionDeclarations.get() && manifest.permissionDeclarations.isNotEmpty()) {
            sections.add(Section("permission", manifest.permissionDeclarations.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardSupportsScreens.get() && manifest.supportsScreens != null) {
            val lines = manifest.supportsScreens!!.toBaselineLines()
            if (lines.isNotEmpty()) sections.add(Section("supports-screens", lines))
        }
        if (guardCompatibleScreens.get() && manifest.compatibleScreens.isNotEmpty()) {
            sections.add(Section("compatible-screens", manifest.compatibleScreens))
        }
        if (guardUsesConfiguration.get() && manifest.usesConfiguration != null) {
            val lines = manifest.usesConfiguration!!.toBaselineLines()
            if (lines.isNotEmpty()) sections.add(Section("uses-configuration", lines))
        }
        if (guardSupportsGlTexture.get() && manifest.supportsGlTextures.isNotEmpty()) {
            sections.add(Section("supports-gl-texture", manifest.supportsGlTextures.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardQueries.get() && manifest.queries != null) {
            val lines = manifest.queries!!.toBaselineLines()
            if (lines.isNotEmpty()) sections.add(Section("queries", lines))
        }

        // Application-level categories
        fun componentLines(components: List<ManifestComponent>): List<String> {
            val lines = mutableListOf<String>()
            for (comp in components.sortedBy { it.name }) {
                val line = baselineMap(comp.toBaselineString()) ?: continue
                lines.add(line)
                if (showIntentFilters && comp.intentFilters.isNotEmpty()) {
                    for (filter in comp.intentFilters) {
                        lines.add("  intent-filter:")
                        filter.actions.forEach { lines.add("    action: $it") }
                        filter.categories.forEach { lines.add("    category: $it") }
                        filter.dataSpecs.forEach { lines.add("    data: $it") }
                    }
                }
            }
            return lines
        }

        if (guardActivities.get() && manifest.activities.isNotEmpty()) {
            sections.add(Section("activity", componentLines(manifest.activities)))
        }
        if (guardActivityAliases.get() && manifest.activityAliases.isNotEmpty()) {
            sections.add(Section("activity-alias", componentLines(manifest.activityAliases)))
        }
        if (guardServices.get() && manifest.services.isNotEmpty()) {
            sections.add(Section("service", componentLines(manifest.services)))
        }
        if (guardReceivers.get() && manifest.receivers.isNotEmpty()) {
            sections.add(Section("receiver", componentLines(manifest.receivers)))
        }
        if (guardMetaData.get() && manifest.metaData.isNotEmpty()) {
            sections.add(Section("meta-data", manifest.metaData.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardProviders.get() && manifest.providers.isNotEmpty()) {
            sections.add(Section("provider", componentLines(manifest.providers)))
        }
        if (guardUsesLibrary.get() && manifest.usesLibraries.isNotEmpty()) {
            sections.add(Section("uses-library", manifest.usesLibraries.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardUsesNativeLibrary.get() && manifest.usesNativeLibraries.isNotEmpty()) {
            sections.add(Section("uses-native-library", manifest.usesNativeLibraries.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardProfileable.get() && manifest.profileable != null) {
            val lines = manifest.profileable!!.toBaselineLines()
            if (lines.isNotEmpty()) sections.add(Section("profileable", lines))
        }
        if (guardStartup.get() && manifest.startupInitializers.isNotEmpty()) {
            sections.add(Section("androidx.startup", manifest.startupInitializers))
        }

        // Render
        val manifestSections = sections.filter { it.tag in manifestLevel }
        val appSections = sections.filter { it.tag in applicationLevel }

        if (manifestSections.isNotEmpty()) {
            appendLine("<manifest>")
            for ((i, section) in manifestSections.withIndex()) {
                appendLine("${section.tag}:")
                section.lines.forEach { appendLine("  $it") }
                if (i < manifestSections.size - 1) appendLine()
            }
        }

        if (manifestSections.isNotEmpty() && appSections.isNotEmpty()) {
            appendLine()
        }

        if (appSections.isNotEmpty()) {
            appendLine("<application>")
            for ((i, section) in appSections.withIndex()) {
                appendLine("${section.tag}:")
                section.lines.forEach { appendLine("  $it") }
                if (i < appSections.size - 1) appendLine()
            }
        }
    }

    internal fun setParams(
        config: ManifestGuardConfiguration,
        mergedManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        projectPath: String,
        baselineDirectory: org.gradle.api.file.Directory,
        filePrefix: String,
        shouldBaseline: Boolean,
    ) {
        this.mergedManifestFile.set(mergedManifest)
        this.configurationName.set(config.configurationName)
        this.projectPath.set(projectPath)
        this.shouldBaseline.set(shouldBaseline)
        applyConfig(config)
        this.baselineDir.set(baselineDirectory)
        this.filePrefix.set(filePrefix)
        this.allowedFilter.set(config.allowedFilter)
        this.baselineMap.set(config.baselineMap)

        declareCompatibilities()
    }
}
