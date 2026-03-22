package io.github.fornewid.gradle.plugins.manifestshield.internal.list

import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldConfiguration
import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldPlugin
import io.github.fornewid.gradle.plugins.manifestshield.internal.ShieldFlags
import io.github.fornewid.gradle.plugins.manifestshield.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.manifestshield.internal.ManifestVisitor
import io.github.fornewid.gradle.plugins.manifestshield.internal.applyConfig
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.ManifestListDiff
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.ManifestListDiffResult
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.ManifestListDiffResult.BaselineCreated
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.ManifestListDiffResult.DiffPerformed.HasDiff
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.ManifestListDiffResult.DiffPerformed.NoDiff
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.Messaging
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.OutputFileUtils
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.Tasks.declareCompatibilities
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestEntry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

internal abstract class ManifestShieldListTask : DefaultTask(), ShieldFlags {

    init {
        group = ManifestShieldPlugin.MANIFEST_SHIELD_TASK_GROUP
    }

    @get:InputFile
    abstract val mergedManifestFile: RegularFileProperty

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val shouldBaseline: Property<Boolean>

    abstract override val guardUsesSdk: Property<Boolean>
    abstract override val guardUsesPermission: Property<Boolean>
    abstract override val guardUsesPermissionSdk23: Property<Boolean>
    abstract override val guardPermission: Property<Boolean>
    abstract override val guardActivity: Property<Boolean>
    abstract override val guardActivityAlias: Property<Boolean>
    abstract override val guardService: Property<Boolean>
    abstract override val guardReceiver: Property<Boolean>
    abstract override val guardProvider: Property<Boolean>
    abstract override val guardUsesFeature: Property<Boolean>
    abstract override val guardIntentFilter: Property<Boolean>
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
        val showIntentFilters = guardIntentFilter.get()

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
        if (guardUsesFeature.get()) entries.add("uses-feature" to manifest.usesFeature)
        if (guardUsesPermission.get()) entries.add("uses-permission" to manifest.usesPermission)
        if (guardPermission.get()) entries.add("permission" to manifest.permission)
        if (guardActivity.get()) entries.add("activity" to manifest.activity)
        if (guardActivityAlias.get()) entries.add("activity-alias" to manifest.activityAlias)
        if (guardService.get()) entries.add("service" to manifest.service)
        if (guardReceiver.get()) entries.add("receiver" to manifest.receiver)
        if (guardProvider.get()) entries.add("provider" to manifest.provider)
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
        val sdk = manifest.usesSdk
        if (guardUsesSdk.get() && sdk != null) {
            val sdkLines = mutableListOf<String>()
            sdk.minSdkVersion?.let { sdkLines.add("minSdkVersion=$it") }
            sdk.targetSdkVersion?.let { sdkLines.add("targetSdkVersion=$it") }
            if (sdkLines.isNotEmpty()) sections.add(Section("uses-sdk", sdkLines))
        }

        // Entry-based manifest-level categories
        if (guardUsesFeature.get() && manifest.usesFeature.isNotEmpty()) {
            sections.add(Section("uses-feature", manifest.usesFeature.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardUsesPermission.get() && manifest.usesPermission.isNotEmpty()) {
            sections.add(Section("uses-permission", manifest.usesPermission.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardUsesPermissionSdk23.get() && manifest.usesPermissionSdk23.isNotEmpty()) {
            sections.add(Section("uses-permission-sdk-23", manifest.usesPermissionSdk23.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardPermission.get() && manifest.permission.isNotEmpty()) {
            sections.add(Section("permission", manifest.permission.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
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
                if (showIntentFilters && comp.intentFilter.isNotEmpty()) {
                    for (filter in comp.intentFilter) {
                        lines.add("  intent-filter:")
                        filter.actions.forEach { lines.add("    action: $it") }
                        filter.categories.forEach { lines.add("    category: $it") }
                        filter.dataSpecs.forEach { lines.add("    data: $it") }
                    }
                }
            }
            return lines
        }

        if (guardActivity.get() && manifest.activity.isNotEmpty()) {
            sections.add(Section("activity", componentLines(manifest.activity)))
        }
        if (guardActivityAlias.get() && manifest.activityAlias.isNotEmpty()) {
            sections.add(Section("activity-alias", componentLines(manifest.activityAlias)))
        }
        if (guardService.get() && manifest.service.isNotEmpty()) {
            sections.add(Section("service", componentLines(manifest.service)))
        }
        if (guardReceiver.get() && manifest.receiver.isNotEmpty()) {
            sections.add(Section("receiver", componentLines(manifest.receiver)))
        }
        if (guardMetaData.get() && manifest.metaData.isNotEmpty()) {
            sections.add(Section("meta-data", manifest.metaData.mapNotNull { baselineMap(it.toBaselineString()) }.sorted()))
        }
        if (guardProvider.get() && manifest.provider.isNotEmpty()) {
            sections.add(Section("provider", componentLines(manifest.provider)))
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
        config: ManifestShieldConfiguration,
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
