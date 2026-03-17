package io.github.fornewid.gradle.plugins.manifestguard

import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestTreeDiffTaskNames
import io.github.fornewid.gradle.plugins.manifestguard.internal.list.ManifestGuardListTask
import io.github.fornewid.gradle.plugins.manifestguard.internal.tree.ManifestTreeDiffTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * A plugin for watching dependency changes
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

        val manifestGuardBaselineTask = registerManifestGuardBaselineTask(target, extension)
        val manifestGuardTask = registerManifestGuardTask(target, extension)
        registerTreeDiffTasks(
            target = target,
            extension = extension,
            baselineTask = manifestGuardBaselineTask,
            guardTask = manifestGuardTask
        )

        attachToCheckTask(
            target = target,
            manifestGuardTask = manifestGuardTask
        )
    }

    private fun attachToCheckTask(target: Project, manifestGuardTask: TaskProvider<ManifestGuardListTask>) {
        // Only add to the "check" lifecycle task if the base plugin is applied
        target.pluginManager.withPlugin("base") {
            // Attach the "manifestGuard" task to the "check" lifecycle task
            target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
                this.dependsOn(manifestGuardTask)
            }
        }
    }

    private fun registerManifestGuardBaselineTask(
        target: Project,
        extension: ManifestGuardPluginExtension
    ): TaskProvider<ManifestGuardListTask> {
        return target.tasks.register(
            MANIFEST_GUARD_BASELINE_TASK_NAME,
            ManifestGuardListTask::class.java
        ) {
            val task = this
            task.setParams(
                project = target,
                extension = extension,
                shouldBaseline = true
            )
        }
    }

    private fun registerManifestGuardTask(
        target: Project,
        extension: ManifestGuardPluginExtension
    ): TaskProvider<ManifestGuardListTask> {
        return target.tasks.register(
            MANIFEST_GUARD_TASK_NAME,
            ManifestGuardListTask::class.java
        ) {
            setParams(
                project = target,
                extension = extension,
                shouldBaseline = false
            )
        }
    }

    private fun registerTreeDiffTasks(
        target: Project,
        extension: ManifestGuardPluginExtension,
        baselineTask: TaskProvider<ManifestGuardListTask>,
        guardTask: TaskProvider<ManifestGuardListTask>
    ) {
        extension.configurations.all {
            val manifestGuardConfiguration = this
            if (manifestGuardConfiguration.tree) {
                val taskClass = ManifestTreeDiffTask::class.java

                val treeGuardTask = target.tasks.register(
                    ManifestTreeDiffTaskNames.createManifestTreeTaskNameForConfiguration(
                        configurationName = manifestGuardConfiguration.configurationName
                    ),
                    taskClass
                ) {
                    setParams(
                        project = target,
                        configurationName = manifestGuardConfiguration.configurationName,
                        shouldBaseline = false
                    )
                }
                guardTask.configure {
                    dependsOn(treeGuardTask)
                }

                val treeBaselineTask = target.tasks.register(
                    ManifestTreeDiffTaskNames.createManifestTreeBaselineTaskNameForConfiguration(
                        configurationName = manifestGuardConfiguration.configurationName
                    ),
                    taskClass
                ) {
                    setParams(
                        project = target,
                        configurationName = manifestGuardConfiguration.configurationName,
                        shouldBaseline = true
                    )
                }
                baselineTask.configure {
                    dependsOn(treeBaselineTask)
                }
            }
        }
    }
}
