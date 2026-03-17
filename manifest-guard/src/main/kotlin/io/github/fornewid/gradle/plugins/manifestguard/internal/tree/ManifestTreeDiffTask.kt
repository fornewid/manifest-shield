package io.github.fornewid.gradle.plugins.manifestguard.internal.tree

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin
import io.github.fornewid.gradle.plugins.manifestguard.internal.getResolvedComponentResult
import io.github.fornewid.gradle.plugins.manifestguard.internal.projectConfigurations
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestGuardTreeDiffer
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.Tasks.declareCompatibilities
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

internal abstract class ManifestTreeDiffTask : DefaultTask() {

    init {
        group = ManifestGuardPlugin.MANIFEST_GUARD_TASK_GROUP
    }

    @get:Input
    abstract val resolvedComponentResult: Property<Provider<ResolvedComponentResult>>

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val shouldBaseline: Property<Boolean>

    @get:OutputFile
    abstract val buildDirOutputFile: Property<File>

    @get:OutputFile
    abstract val projectDirOutputFile: Property<File>

    fun setParams(
        project: Project,
        configurationName: String,
        shouldBaseline: Boolean,
    ) {
        val projectDependenciesDir = OutputFileUtils.projectDirDependenciesDir(project)
        val projectDirOutputFile: File = ManifestGuardTreeDiffer.projectDirOutputFile(
            projectDirectory = projectDependenciesDir,
            configurationName = configurationName
        )
        val buildDirOutputFile: File = ManifestGuardTreeDiffer.buildDirOutputFile(
            buildDirectory = project.layout.buildDirectory.get(),
            configurationName = configurationName
        )
        val projectPath = project.path

        val resolvedComponentResult: Provider<ResolvedComponentResult> =
            project.projectConfigurations.getResolvedComponentResult(configurationName)
                ?: throw GradleException(buildString {
                    appendLine("Manifest Guard could not resolve configuration $configurationName for $projectPath.")
                    appendLine("Please update your configuration and try again.")
                })

        this.resolvedComponentResult.set(resolvedComponentResult)
        this.projectDirOutputFile.set(projectDirOutputFile)
        this.buildDirOutputFile.set(buildDirOutputFile)
        this.configurationName.set(configurationName)
        this.projectPath.set(projectPath)
        this.shouldBaseline.set(shouldBaseline)

        declareCompatibilities()
    }

    @TaskAction
    fun generate() {
        // USES INTERNAL API
        val asciiRenderer = AsciiManifestReportRenderer2()

        val resolvedComponentResult: Provider<ResolvedComponentResult> = resolvedComponentResult.get()
        asciiRenderer.setOutputFile(buildDirOutputFile.get())
        asciiRenderer.prepareVisit()
        asciiRenderer.render(resolvedComponentResult.get())

        ManifestGuardTreeDiffer(
            projectPath = projectPath.get(),
            configurationName = configurationName.get(),
            shouldBaseline = shouldBaseline.get(),
            projectDirOutputFile = projectDirOutputFile.get(),
            buildDirOutputFile = buildDirOutputFile.get(),
        ).performDiff()
    }
}
