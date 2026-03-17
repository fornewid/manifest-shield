package io.github.fornewid.gradle.plugins.manifestguard.internal.tree

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import io.github.fornewid.gradle.plugins.manifestguard.internal.BlameLogParser
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestVisitor
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.BaselineCreated
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.HasDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.NoDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Messaging
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Tasks.declareCompatibilities
import io.github.fornewid.gradle.plugins.manifestguard.models.ComponentType
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestFeature
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestPermission
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class ManifestTreeDiffTask : DefaultTask() {

    init {
        group = ManifestGuardPlugin.MANIFEST_GUARD_TASK_GROUP
    }

    @get:InputFile
    abstract val mergedManifestFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    abstract val blameLogFile: RegularFileProperty

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val shouldBaseline: Property<Boolean>

    @get:Input
    abstract val guardPermissions: Property<Boolean>

    @get:Input
    abstract val guardActivities: Property<Boolean>

    @get:Input
    abstract val guardServices: Property<Boolean>

    @get:Input
    abstract val guardReceivers: Property<Boolean>

    @get:Input
    abstract val guardProviders: Property<Boolean>

    @get:Input
    abstract val guardFeatures: Property<Boolean>

    @get:OutputDirectory
    abstract val baselineDir: DirectoryProperty

    @get:Input
    abstract val baselineMap: Property<(String) -> String?>

    @TaskAction
    internal fun execute() {
        val manifest = ManifestVisitor.parse(mergedManifestFile.get().asFile)
        val configName = configurationName.get()
        val path = projectPath.get()
        val mapper = baselineMap.get()
        val baseline = shouldBaseline.get()
        val dir = baselineDir.get()

        val sourceMap = if (blameLogFile.isPresent) {
            val blameFile = blameLogFile.get().asFile
            if (blameFile.exists()) {
                BlameLogParser.buildSourceMap(BlameLogParser.parse(blameFile))
            } else {
                logger.warn("Manifest merger blame log not found. Attribution will be unavailable.")
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val exceptionMessage = StringBuilder()

        if (guardPermissions.get()) {
            processTreeCategory(
                entries = manifest.permissions,
                elementType = "uses-permission",
                category = "permissions",
                sourceMap = sourceMap,
                configurationName = configName,
                projectPath = path,
                baselineMap = mapper,
                shouldBaseline = baseline,
                dir = dir,
                exceptionMessage = exceptionMessage,
            )
        }

        for ((type, flag, entries) in listOf(
            Triple(ComponentType.ACTIVITY, guardActivities, manifest.activities),
            Triple(ComponentType.SERVICE, guardServices, manifest.services),
            Triple(ComponentType.RECEIVER, guardReceivers, manifest.receivers),
            Triple(ComponentType.PROVIDER, guardProviders, manifest.providers),
        )) {
            if (flag.get()) {
                processTreeCategory(
                    entries = entries,
                    elementType = type.tagName,
                    category = "${type.tagName}s",
                    sourceMap = sourceMap,
                    configurationName = configName,
                    projectPath = path,
                    baselineMap = mapper,
                    shouldBaseline = baseline,
                    dir = dir,
                    exceptionMessage = exceptionMessage,
                )
            }
        }

        if (guardFeatures.get()) {
            processTreeCategory(
                entries = manifest.features,
                elementType = "uses-feature",
                category = "features",
                sourceMap = sourceMap,
                configurationName = configName,
                projectPath = path,
                baselineMap = mapper,
                shouldBaseline = baseline,
                dir = dir,
                exceptionMessage = exceptionMessage,
            )
        }

        exceptionMessage.toString().takeIf(String::isNotEmpty)?.let {
            throw GradleException(it)
        }
    }

    private fun processTreeCategory(
        entries: List<ManifestEntry>,
        elementType: String,
        category: String,
        sourceMap: Map<String, String>,
        configurationName: String,
        projectPath: String,
        baselineMap: (String) -> String?,
        shouldBaseline: Boolean,
        dir: org.gradle.api.file.Directory,
        exceptionMessage: StringBuilder,
    ) {
        val treeContent = buildTreeContent(entries, elementType, sourceMap, baselineMap)
        val baselineFile = OutputFileUtils.baselineFile(dir, "$category.tree")

        val result = writeAndDiff(baselineFile, treeContent, projectPath, configurationName, "$category.tree", shouldBaseline)

        when (result) {
            is HasDiff -> {
                val rebaselineMsg = Messaging.rebaselineMessage(projectPath, configurationName)
                logger.error(result.createDiffMessage(withColor = true, rebaselineMessage = rebaselineMsg))
                exceptionMessage.appendLine(result.createDiffMessage(withColor = false, rebaselineMessage = rebaselineMsg))
            }
            is NoDiff -> logger.debug(result.noDiffMessage)
            is BaselineCreated -> logger.lifecycle(result.baselineCreatedMessage(withColor = true))
        }
    }

    private fun buildTreeContent(
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

    private fun writeAndDiff(
        baselineFile: File,
        reportContent: String,
        projectPath: String,
        configurationName: String,
        category: String,
        shouldBaseline: Boolean,
    ): ManifestListDiffResult {
        return if (shouldBaseline || !baselineFile.exists()) {
            baselineFile.writeText(reportContent)
            BaselineCreated(projectPath = projectPath, configurationName = configurationName, category = category, baselineFile = baselineFile)
        } else {
            ManifestListDiff.performDiff(
                projectPath = projectPath,
                configurationName = configurationName,
                category = category,
                expectedContent = baselineFile.readText(),
                actualContent = reportContent,
            )
        }
    }

    internal fun setParams(
        config: ManifestGuardConfiguration,
        mergedManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        blameLog: File?,
        projectPath: String,
        baselineDirectory: org.gradle.api.file.Directory,
        shouldBaseline: Boolean,
    ) {
        this.mergedManifestFile.set(mergedManifest)
        if (blameLog != null && blameLog.exists()) {
            this.blameLogFile.set(blameLog)
        }
        this.configurationName.set(config.configurationName)
        this.projectPath.set(projectPath)
        this.shouldBaseline.set(shouldBaseline)
        this.guardPermissions.set(config.permissions)
        this.guardActivities.set(config.activities)
        this.guardServices.set(config.services)
        this.guardReceivers.set(config.receivers)
        this.guardProviders.set(config.providers)
        this.guardFeatures.set(config.features)
        this.baselineDir.set(baselineDirectory)
        this.baselineMap.set(config.baselineMap)

        declareCompatibilities()
    }
}
