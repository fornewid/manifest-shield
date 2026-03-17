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
import java.io.File

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

        val exceptionMessage = StringBuilder()

        if (guardPermissions.get()) {
            processCategory(manifest.permissions, "permissions", configName, path, filter, mapper, baseline, dir, exceptionMessage)
        }
        if (guardActivities.get()) {
            processCategory(manifest.activities, "activities", configName, path, filter, mapper, baseline, dir, exceptionMessage)
        }
        if (guardServices.get()) {
            processCategory(manifest.services, "services", configName, path, filter, mapper, baseline, dir, exceptionMessage)
        }
        if (guardReceivers.get()) {
            processCategory(manifest.receivers, "receivers", configName, path, filter, mapper, baseline, dir, exceptionMessage)
        }
        if (guardProviders.get()) {
            processCategory(manifest.providers, "providers", configName, path, filter, mapper, baseline, dir, exceptionMessage)
        }
        if (guardFeatures.get()) {
            processCategory(manifest.features, "features", configName, path, filter, mapper, baseline, dir, exceptionMessage)
        }

        exceptionMessage.toString().takeIf(String::isNotEmpty)?.let {
            throw GradleException(it)
        }
    }

    private fun processCategory(
        entries: List<ManifestEntry>,
        category: String,
        configurationName: String,
        projectPath: String,
        allowedFilter: (String) -> Boolean,
        baselineMap: (String) -> String?,
        shouldBaseline: Boolean,
        dir: org.gradle.api.file.Directory,
        exceptionMessage: StringBuilder,
    ) {
        val disallowed = entries.filter { !allowedFilter(it.name) }
        if (disallowed.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Disallowed manifest entries found in $projectPath ($configurationName/$category):")
                disallowed.forEach { appendLine("  \"${it.name}\"") }
                appendLine()
                appendLine("These entries must be removed based on the configured 'allowedFilter'.")
            })
        }

        val reportContent = entries
            .mapNotNull { entry -> baselineMap(entry.toBaselineString()) }
            .joinToString("\n", postfix = if (entries.isNotEmpty()) "\n" else "")

        val baselineFile = OutputFileUtils.baselineFile(dir, category)

        val result = writeAndDiff(baselineFile, reportContent, projectPath, configurationName, category, shouldBaseline)

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
        projectPath: String,
        baselineDirectory: org.gradle.api.file.Directory,
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
        this.allowedFilter.set(config.allowedFilter)
        this.baselineMap.set(config.baselineMap)

        declareCompatibilities()
    }
}
