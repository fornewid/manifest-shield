package io.github.fornewid.gradle.plugins.manifestguard.internal.list

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPluginExtension
import io.github.fornewid.gradle.plugins.manifestguard.internal.ConfigurationValidators
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestGuardListReportWriter
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestGuardReportData
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestGuardReportType
import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestVisitor
import io.github.fornewid.gradle.plugins.manifestguard.internal.getResolvedComponentResult
import io.github.fornewid.gradle.plugins.manifestguard.internal.isRootProject
import io.github.fornewid.gradle.plugins.manifestguard.internal.projectConfigurations
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.BaselineCreated
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.HasDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult.DiffPerformed.NoDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Tasks.declareCompatibilities
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

internal abstract class ManifestGuardListTask : DefaultTask() {

    init {
        group = ManifestGuardPlugin.MANIFEST_GUARD_TASK_GROUP
    }

    private fun generateReportForConfiguration(
        manifestGuardConfiguration: ManifestGuardConfiguration,
        resolvedComponentResult: ResolvedComponentResult,
    ): ManifestGuardReportData {
        val configurationName = manifestGuardConfiguration.configurationName

        val dependencies = ManifestVisitor.traverseComponentDependencies(resolvedComponentResult)

        return ManifestGuardReportData(
            projectPath = projectPath.get(),
            configurationName = configurationName,
            allowedFilter = manifestGuardConfiguration.allowedFilter,
            baselineMap = manifestGuardConfiguration.baselineMap,
            dependencies = dependencies,
        )
    }

    @get:Input
    abstract val shouldBaseline: Property<Boolean>

    @get:Input
    abstract val forRootProject: Property<Boolean>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val monitoredConfigurationsMap: MapProperty<ManifestGuardConfiguration, Provider<ResolvedComponentResult>>

    @get:OutputDirectory
    abstract val projectDirectoryDependenciesDir: DirectoryProperty

    @Suppress("NestedBlockDepth")
    @TaskAction
    internal fun execute() {
        val manifestGuardConfigurations = monitoredConfigurationsMap.get()

        val reports = manifestGuardConfigurations.map { generateReportForConfiguration(it.key, it.value.get()) }

        // Throw Error if any Disallowed Dependencies are Found
        val reportsWithDisallowedDependencies = reports.filter { it.disallowed.isNotEmpty() }
        if (reportsWithDisallowedDependencies.isNotEmpty()) {
            throwExceptionAboutDisallowedDependencies(reportsWithDisallowedDependencies)
        }

        // Perform Diffs and Write Baselines
        val exceptionMessage = StringBuilder()
        manifestGuardConfigurations.keys.forEach { manifestGuardConfig ->
            val report = reports.firstOrNull { it.configurationName == manifestGuardConfig.configurationName } ?: return@forEach
            when (val diff = writeListReport(manifestGuardConfig, report)) {
                is HasDiff -> {
                    // Print to console in color
                    logger.error(diff.createDiffMessage(withColor = true))

                    // Add to exception message without color
                    exceptionMessage.appendLine(diff.createDiffMessage(withColor = false))
                }
                is NoDiff -> logger.debug(diff.noDiffMessage)
                is BaselineCreated -> logger.lifecycle(diff.baselineCreatedMessage(withColor = true))
            }
        }

        // If there was an exception message, throw an Exception with the message
        exceptionMessage.toString().takeIf(String::isNotEmpty)?.let {
            throw GradleException(it)
        }
    }

    /**
     * @return Whether changes were detected
     */
    private fun writeListReport(
        manifestGuardConfig: ManifestGuardConfiguration,
        report: ManifestGuardReportData
    ): ManifestListDiffResult {
        val reportType = ManifestGuardReportType.LIST
        val reportWriter = ManifestGuardListReportWriter(
            artifacts = manifestGuardConfig.artifacts,
            modules = manifestGuardConfig.modules
        )

        return reportWriter.writeReport(
            projectDirOutputFile = OutputFileUtils.projectDirOutputFile(
                projectDirectory = projectDirectoryDependenciesDir.get(),
                configurationName = report.configurationName,
                reportType = reportType,
            ),
            report = report,
            shouldBaseline = shouldBaseline.get()
        )
    }

    private fun throwExceptionAboutDisallowedDependencies(reportsWithDisallowedDependencies: List<ManifestGuardReportData>) {
        val errorMessage = buildString {
            reportsWithDisallowedDependencies.forEach { report ->
                val disallowed = report.disallowed
                appendLine(
                    """Disallowed Dependencies found in ${projectPath.get()} for the configuration "${report.configurationName}" """
                )
                disallowed.forEach {
                    appendLine("\"${it.name}\",")
                }
                appendLine()
            }
            appendLine("These dependencies are included and must be removed based on the configured 'allowedFilter'.")
            appendLine()
        }

        throw GradleException(errorMessage)
    }

    internal fun setParams(
        project: Project,
        extension: ManifestGuardPluginExtension,
        shouldBaseline: Boolean
    ) {
        val resolvedConfigurationsMap = resolveMonitoredConfigurationsMap(
            project = project,
            monitoredConfigurations = extension.configurations
        )

        ConfigurationValidators.validatePluginConfiguration(
            project = project,
            extension = extension,
            resolvedConfigurationsMap = resolvedConfigurationsMap,
        )

        this.forRootProject.set(project.isRootProject())
        this.projectPath.set(project.path)
        this.monitoredConfigurationsMap.set(resolvedConfigurationsMap)
        this.shouldBaseline.set(shouldBaseline)
        val projectDirDependenciesDir = OutputFileUtils.projectDirDependenciesDir(project)
        this.projectDirectoryDependenciesDir.set(projectDirDependenciesDir)

        declareCompatibilities()
    }

    /**
     * Attempts to resolve configurations, and returns only the resolved ones
     */
    private fun resolveMonitoredConfigurationsMap(
        project: Project,
        monitoredConfigurations: Collection<ManifestGuardConfiguration>,
    ): Map<ManifestGuardConfiguration, Provider<ResolvedComponentResult>> {
        val resolvedConfigurationsMap = mutableMapOf<ManifestGuardConfiguration, Provider<ResolvedComponentResult>>()
        monitoredConfigurations.forEach { monitoredConfiguration ->
            val resolvedComponentResultForConfiguration =
                project.projectConfigurations.getResolvedComponentResult(monitoredConfiguration.configurationName)
            if (resolvedComponentResultForConfiguration != null) {
                resolvedConfigurationsMap[monitoredConfiguration] = resolvedComponentResultForConfiguration
            }
        }
        return resolvedConfigurationsMap
    }

}
