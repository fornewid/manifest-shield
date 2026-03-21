package io.github.fornewid.gradle.plugins.manifestguard.internal.list

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestVisitor
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.BaselineCreated
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.HasDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.NoDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Messaging
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Tasks.declareCompatibilities
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

internal abstract class ManifestGuardListTask : DefaultTask() {

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

        // Check for disallowed entries across all categories
        val allEntries = mutableListOf<Pair<String, List<ManifestEntry>>>()
        if (guardFeatures.get()) allEntries.add("uses-feature" to manifest.features)
        if (guardPermissions.get()) allEntries.add("uses-permission" to manifest.permissions)
        if (guardActivities.get()) allEntries.add("activity" to manifest.activities)
        if (guardServices.get()) allEntries.add("service" to manifest.services)
        if (guardReceivers.get()) allEntries.add("receiver" to manifest.receivers)
        if (guardProviders.get()) allEntries.add("provider" to manifest.providers)

        for ((category, entries) in allEntries) {
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

        // Build merged content with sections
        val reportContent = buildMergedContent(allEntries, mapper)

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

    private fun buildMergedContent(
        categories: List<Pair<String, List<ManifestEntry>>>,
        baselineMap: (String) -> String?,
    ): String = buildString {
        val manifestLevel = listOf("uses-feature", "uses-permission")
        val applicationLevel = listOf("activity", "service", "receiver", "provider")

        val manifestCategories = categories.filter { it.first in manifestLevel && it.second.isNotEmpty() }
        val appCategories = categories.filter { it.first in applicationLevel && it.second.isNotEmpty() }

        if (manifestCategories.isNotEmpty()) {
            appendLine("<manifest>")
            for ((i, pair) in manifestCategories.withIndex()) {
                val (tag, entries) = pair
                appendLine("$tag:")
                entries.mapNotNull { baselineMap(it.toBaselineString()) }
                    .sorted()
                    .forEach { appendLine("  $it") }
                if (i < manifestCategories.size - 1) appendLine()
            }
        }

        if (manifestCategories.isNotEmpty() && appCategories.isNotEmpty()) {
            appendLine()
        }

        if (appCategories.isNotEmpty()) {
            appendLine("<application>")
            for ((i, pair) in appCategories.withIndex()) {
                val (tag, entries) = pair
                appendLine("$tag:")
                entries.mapNotNull { baselineMap(it.toBaselineString()) }
                    .sorted()
                    .forEach { appendLine("  $it") }
                if (i < appCategories.size - 1) appendLine()
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
        this.guardPermissions.set(config.permissions)
        this.guardActivities.set(config.activities)
        this.guardServices.set(config.services)
        this.guardReceivers.set(config.receivers)
        this.guardProviders.set(config.providers)
        this.guardFeatures.set(config.features)
        this.baselineDir.set(baselineDirectory)
        this.filePrefix.set(filePrefix)
        this.allowedFilter.set(config.allowedFilter)
        this.baselineMap.set(config.baselineMap)

        declareCompatibilities()
    }
}
