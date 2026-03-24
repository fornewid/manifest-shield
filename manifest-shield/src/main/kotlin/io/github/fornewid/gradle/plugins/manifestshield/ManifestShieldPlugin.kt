package io.github.fornewid.gradle.plugins.manifestshield

import io.github.fornewid.gradle.plugins.manifestshield.internal.AndroidVariantHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.util.Properties

/**
 * A plugin for guarding against unintentional AndroidManifest.xml changes
 */
public class ManifestShieldPlugin : Plugin<Project> {

    internal companion object {
        internal const val MANIFEST_SHIELD_TASK_GROUP = "Manifest Shield"

        internal const val MANIFEST_SHIELD_EXTENSION_NAME = "manifestShield"

        internal const val MANIFEST_SHIELD_TASK_NAME = "manifestShield"

        internal const val MANIFEST_SHIELD_BASELINE_TASK_NAME = "manifestShieldBaseline"

        internal val VERSION: String by lazy {
            ManifestShieldPlugin::class.java
                .getResourceAsStream("/manifest-shield.properties")
                ?.let { Properties().apply { load(it) }.getProperty("version") }
                ?: "dev"
        }
    }

    override fun apply(target: Project) {
        val extension = target.extensions.create(
            MANIFEST_SHIELD_EXTENSION_NAME,
            ManifestShieldPluginExtension::class.java,
            target.objects
        )

        val guardTask = target.tasks.register(MANIFEST_SHIELD_TASK_NAME) {
            group = MANIFEST_SHIELD_TASK_GROUP
            description = "Guard against unintentional manifest changes"
        }
        val baselineTask = target.tasks.register(MANIFEST_SHIELD_BASELINE_TASK_NAME) {
            group = MANIFEST_SHIELD_TASK_GROUP
            description = "Save current manifest as baseline"
        }

        // Only application modules produce a fully merged manifest that includes
        // all transitive dependency manifests. Library modules only merge their own
        // manifest with direct dependencies, making their baselines incomplete.
        target.pluginManager.withPlugin("com.android.application") {
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
