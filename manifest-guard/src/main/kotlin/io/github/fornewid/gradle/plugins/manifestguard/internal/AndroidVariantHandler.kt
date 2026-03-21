package io.github.fornewid.gradle.plugins.manifestguard.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPluginExtension
import io.github.fornewid.gradle.plugins.manifestguard.internal.list.ManifestGuardListTask
import io.github.fornewid.gradle.plugins.manifestguard.internal.tree.ManifestTreeDiffTask
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Isolated handler for AGP-specific configuration.
 * Separated from [ManifestGuardPlugin][io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin]
 * to avoid classloader issues with GradleRunner TestKit.
 */
internal object AndroidVariantHandler {

    fun configureVariants(
        project: Project,
        extension: ManifestGuardPluginExtension,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            extension.configurations.configureEach {
                if (configurationName == variant.name) {
                    registerTasks(project, extension.baselineDir, this, variant, guardTask, baselineTask)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }

    private fun registerTasks(
        project: Project,
        baselineDir: String,
        config: ManifestGuardConfiguration,
        variant: Variant,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val capitalizedName = config.configurationName.capitalize()
        val baselineDirectory = OutputFileUtils.manifestGuardDir(project, baselineDir, config.configurationName)
        val blameLogFile = project.layout.buildDirectory
            .file("outputs/logs/manifest-merger-${config.configurationName}-report.txt")
            .get().asFile

        val perConfigGuardTask = project.tasks.register(
            "manifestGuard$capitalizedName",
            ManifestGuardListTask::class.java
        ) {
            setParams(config, mergedManifest, project.path, baselineDirectory, false)
        }
        guardTask.configure { dependsOn(perConfigGuardTask) }

        val perConfigBaselineTask = project.tasks.register(
            "manifestGuardBaseline$capitalizedName",
            ManifestGuardListTask::class.java
        ) {
            setParams(config, mergedManifest, project.path, baselineDirectory, true)
        }
        baselineTask.configure { dependsOn(perConfigBaselineTask) }

        if (config.tree) {
            val treeGuardTask = project.tasks.register(
                "manifestGuardTree$capitalizedName",
                ManifestTreeDiffTask::class.java
            ) {
                setParams(config, mergedManifest, blameLogFile, project.path, baselineDirectory, false)
            }
            perConfigGuardTask.configure { dependsOn(treeGuardTask) }

            val treeBaselineTask = project.tasks.register(
                "manifestGuardTreeBaseline$capitalizedName",
                ManifestTreeDiffTask::class.java
            ) {
                setParams(config, mergedManifest, blameLogFile, project.path, baselineDirectory, true)
            }
            perConfigBaselineTask.configure { dependsOn(treeBaselineTask) }
        }
    }
}
