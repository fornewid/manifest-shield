package io.github.fornewid.gradle.plugins.manifestguard.internal.tree

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
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.TreeContentBuilder
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

internal abstract class ManifestTreeDiffTask : DefaultTask(), GuardFlags {

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
    abstract override val guardPermissionDeclarations: Property<Boolean>
    abstract override val guardActivities: Property<Boolean>
    abstract override val guardActivityAliases: Property<Boolean>
    abstract override val guardServices: Property<Boolean>
    abstract override val guardReceivers: Property<Boolean>
    abstract override val guardProviders: Property<Boolean>
    abstract override val guardFeatures: Property<Boolean>
    abstract override val guardIntentFilters: Property<Boolean>
    abstract override val guardStartup: Property<Boolean>

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

        val treeContent = TreeContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            baselineMap = mapper,
            projectPath = path,
            guardSdk = guardSdk.get(),
            guardFeatures = guardFeatures.get(),
            guardPermissions = guardPermissions.get(),
            guardPermissionDeclarations = guardPermissionDeclarations.get(),
            guardActivities = guardActivities.get(),
            guardActivityAliases = guardActivityAliases.get(),
            guardServices = guardServices.get(),
            guardReceivers = guardReceivers.get(),
            guardProviders = guardProviders.get(),
            guardIntentFilters = showIntentFilters,
            guardStartup = guardStartup.get(),
        )
        val baselineFile = OutputFileUtils.baselineFile(dir, "$prefix.tree")
        val category = "$prefix.tree"

        val result = if (baseline || !baselineFile.exists()) {
            baselineFile.writeText(treeContent)
            BaselineCreated(projectPath = path, configurationName = configName, category = category, baselineFile = baselineFile)
        } else {
            ManifestListDiff.performDiff(
                projectPath = path,
                configurationName = configName,
                category = category,
                expectedContent = baselineFile.readText(),
                actualContent = treeContent,
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
