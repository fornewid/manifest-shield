package io.github.fornewid.gradle.plugins.manifestguard

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.manifestguard.internal.list.ManifestGuardListTask
import io.github.fornewid.gradle.plugins.manifestguard.internal.tree.ManifestTreeDiffTask
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.OutputFileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * A plugin for guarding against unintentional AndroidManifest.xml changes
 */
public class ManifestGuardPlugin : Plugin<Project> {

    internal companion object {
        internal const val MANIFEST_GUARD_TASK_GROUP = "Manifest Guard"

        internal const val MANIFEST_GUARD_EXTENSION_NAME = "manifestGuard"

        internal const val MANIFEST_GUARD_TASK_NAME = "manifestGuard"

        internal const val MANIFEST_GUARD_BASELINE_TASK_NAME = "manifestGuardBaseline"
    }

    override fun apply(target: Project) {
        val extension = target.extensions.create(
            MANIFEST_GUARD_EXTENSION_NAME,
            ManifestGuardPluginExtension::class.java,
            target.objects
        )

        val guardTask = target.tasks.register(MANIFEST_GUARD_TASK_NAME) {
            group = MANIFEST_GUARD_TASK_GROUP
            description = "Guard against unintentional manifest changes"
        }
        val baselineTask = target.tasks.register(MANIFEST_GUARD_BASELINE_TASK_NAME) {
            group = MANIFEST_GUARD_TASK_GROUP
            description = "Save current manifest as baseline"
        }

        target.pluginManager.withPlugin("com.android.application") {
            configureAndroidVariants(target, extension, guardTask, baselineTask)
        }
        target.pluginManager.withPlugin("com.android.library") {
            configureAndroidVariants(target, extension, guardTask, baselineTask)
        }
        target.pluginManager.withPlugin("com.android.dynamic-feature") {
            configureAndroidVariants(target, extension, guardTask, baselineTask)
        }

        attachToCheckTask(target, guardTask)
    }

    private fun attachToCheckTask(target: Project, guardTask: TaskProvider<*>) {
        target.pluginManager.withPlugin("base") {
            target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
                this.dependsOn(guardTask)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }

    private fun configureAndroidVariants(
        project: Project,
        extension: ManifestGuardPluginExtension,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            extension.configurations.configureEach {
                if (configurationName == variant.name) {
                    registerTasks(project, this, variant, guardTask, baselineTask)
                }
            }
        }
    }

    private fun registerTasks(
        project: Project,
        config: ManifestGuardConfiguration,
        variant: Variant,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val capitalizedName = config.configurationName.capitalize()
        val baselineDirectory = OutputFileUtils.manifestGuardDir(project, config.configurationName)
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
