package io.github.fornewid.gradle.plugins.manifestguard

import io.github.fornewid.gradle.plugins.manifestguard.internal.AndroidVariantHandler
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

        // AGP types are loaded lazily via AndroidVariantHandler to avoid
        // classloader isolation issues with GradleRunner TestKit.
        target.pluginManager.withPlugin("com.android.application") {
            AndroidVariantHandler.configureVariants(target, extension, guardTask, baselineTask)
        }
        target.pluginManager.withPlugin("com.android.library") {
            AndroidVariantHandler.configureVariants(target, extension, guardTask, baselineTask)
        }
        target.pluginManager.withPlugin("com.android.dynamic-feature") {
            AndroidVariantHandler.configureVariants(target, extension, guardTask, baselineTask)
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
}
