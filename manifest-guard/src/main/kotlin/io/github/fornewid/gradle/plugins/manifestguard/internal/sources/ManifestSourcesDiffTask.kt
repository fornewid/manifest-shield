package io.github.fornewid.gradle.plugins.manifestguard.internal.sources

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import io.github.fornewid.gradle.plugins.manifestguard.internal.BlameLogParser
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
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.SourcesContentBuilder
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestEntry
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

internal abstract class ManifestSourcesDiffTask : DefaultTask(), GuardFlags {

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
    abstract val rootProjectDir: Property<String>

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
    abstract val baselineMap: Property<(String) -> String?>

    @TaskAction
    internal fun execute() {
        val manifest = ManifestVisitor.parse(mergedManifestFile.get().asFile)
        val configName = configurationName.get()
        val path = projectPath.get()
        val mapper = baselineMap.get()
        val baseline = shouldBaseline.get()
        val dir = baselineDir.get()
        val prefix = filePrefix.get()
        val showIntentFilters = guardIntentFilters.get()

        val sourceMap = if (blameLogFile.isPresent) {
            val blameFile = blameLogFile.get().asFile
            if (blameFile.exists()) {
                val rootDir = rootProjectDir.orNull?.let { File(it) }
                BlameLogParser.buildSourceMap(BlameLogParser.parse(blameFile, rootDir))
            } else {
                logger.warn("Manifest merger blame log not found. Attribution will be unavailable.")
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val enabledFlags = mapOf(
            "sdk" to guardSdk.get(),
            "features" to guardFeatures.get(),
            "permissions" to guardPermissions.get(),
            "permissionsSdk23" to guardPermissionsSdk23.get(),
            "permissionDeclarations" to guardPermissionDeclarations.get(),
            "supportsScreens" to guardSupportsScreens.get(),
            "compatibleScreens" to guardCompatibleScreens.get(),
            "usesConfiguration" to guardUsesConfiguration.get(),
            "supportsGlTexture" to guardSupportsGlTexture.get(),
            "queries" to guardQueries.get(),
            "activities" to guardActivities.get(),
            "activityAliases" to guardActivityAliases.get(),
            "metaData" to guardMetaData.get(),
            "services" to guardServices.get(),
            "receivers" to guardReceivers.get(),
            "providers" to guardProviders.get(),
            "usesLibrary" to guardUsesLibrary.get(),
            "usesNativeLibrary" to guardUsesNativeLibrary.get(),
            "profileable" to guardProfileable.get(),
            "intentFilters" to showIntentFilters,
            "startup" to guardStartup.get(),
        )

        val sourcesContent = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            baselineMap = mapper,
            projectPath = path,
            enabledFlags = enabledFlags,
        )
        val baselineFile = OutputFileUtils.baselineFile(dir, "$prefix.sources")
        val category = "$prefix.sources"

        val result = if (baseline || !baselineFile.exists()) {
            baselineFile.writeText(sourcesContent)
            BaselineCreated(projectPath = path, configurationName = configName, category = category, baselineFile = baselineFile)
        } else {
            ManifestListDiff.performDiff(
                projectPath = path,
                configurationName = configName,
                category = category,
                expectedContent = baselineFile.readText(),
                actualContent = sourcesContent,
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

    internal fun setParams(
        config: ManifestGuardConfiguration,
        mergedManifest: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        blameLog: File?,
        projectPath: String,
        rootProjectDir: File,
        baselineDirectory: org.gradle.api.file.Directory,
        filePrefix: String,
        shouldBaseline: Boolean,
    ) {
        this.mergedManifestFile.set(mergedManifest)
        if (blameLog != null && blameLog.exists()) {
            this.blameLogFile.set(blameLog)
        }
        this.configurationName.set(config.configurationName)
        this.projectPath.set(projectPath)
        this.rootProjectDir.set(rootProjectDir.absolutePath)
        this.shouldBaseline.set(shouldBaseline)
        applyConfig(config)
        this.baselineDir.set(baselineDirectory)
        this.filePrefix.set(filePrefix)
        this.baselineMap.set(config.baselineMap)

        declareCompatibilities()
    }
}
