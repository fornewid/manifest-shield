package io.github.fornewid.gradle.plugins.manifestshield.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldConfiguration
import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldPluginExtension
import io.github.fornewid.gradle.plugins.manifestshield.internal.list.ManifestShieldListTask
import io.github.fornewid.gradle.plugins.manifestshield.internal.sources.ManifestSourcesDiffTask
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.OutputFileUtils
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Isolated handler for AGP-specific configuration.
 * Separated from [ManifestShieldPlugin][io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldPlugin]
 * to avoid classloader issues with GradleRunner TestKit.
 */
internal object AndroidVariantHandler {

    fun configureVariants(
        project: Project,
        extension: ManifestShieldPluginExtension,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        val matchedConfigs = mutableSetOf<String>()
        val allVariantNames = mutableListOf<String>()

        androidComponents.onVariants { variant ->
            allVariantNames.add(variant.name)
            extension.configurations.configureEach {
                val variantName = variant.name
                if (configurationName == variantName || configurationName == variantName.toKebabCase()) {
                    matchedConfigs.add(configurationName)
                    registerTasks(project, extension.baselineDir.get(), this, variant, guardTask, baselineTask)
                }
            }
        }

        project.afterEvaluate {
            extension.configurations.configureEach {
                if (configurationName !in matchedConfigs) {
                    val available = allVariantNames.map { it.toKebabCase() }
                    project.logger.warn(
                        "Manifest Shield: Configuration \"$configurationName\" does not match any build variant.\n" +
                            "Available variants: $available"
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }

    /**
     * Converts a camelCase string to kebab-case.
     * e.g., "devRelease" -> "dev-release", "release" -> "release"
     */
    internal fun String.toKebabCase(): String {
        return replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
    }

    /**
     * Converts a kebab-case string to camelCase.
     * e.g., "dev-release" -> "devRelease", "release" -> "release"
     */
    private fun String.toCamelCase(): String {
        return split("-").joinToString("") { it.capitalize() }.replaceFirstChar { it.lowercase() }
    }

    private fun registerTasks(
        project: Project,
        baselineDir: String,
        config: ManifestShieldConfiguration,
        variant: Variant,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val variantCamelCase = variant.name
        val variantKebabCase = variantCamelCase.toKebabCase()
        val capitalizedName = variantCamelCase.capitalize()
        val baselineDirectory = OutputFileUtils.manifestShieldDir(project, baselineDir)
        val filePrefix = "${variantCamelCase}AndroidManifest"
        val blameLogProvider = project.layout.buildDirectory
            .file("outputs/logs/manifest-merger-${variantKebabCase}-report.txt")

        val perConfigGuardTask = project.tasks.register(
            "manifestShield$capitalizedName",
            ManifestShieldListTask::class.java
        ) {
            setParams(config, mergedManifest, project.path, baselineDirectory, filePrefix, false)
        }
        guardTask.configure { dependsOn(perConfigGuardTask) }

        val perConfigBaselineTask = project.tasks.register(
            "manifestShieldBaseline$capitalizedName",
            ManifestShieldListTask::class.java
        ) {
            setParams(config, mergedManifest, project.path, baselineDirectory, filePrefix, true)
        }
        baselineTask.configure { dependsOn(perConfigBaselineTask) }

        if (config.sources) {
            val sourcesGuardTask = project.tasks.register(
                "manifestShieldSources$capitalizedName",
                ManifestSourcesDiffTask::class.java
            ) {
                dependsOn(mergedManifest)
                setParams(config, mergedManifest, blameLogProvider, project.path, project.rootDir, baselineDirectory, filePrefix, false)
            }
            perConfigGuardTask.configure { dependsOn(sourcesGuardTask) }

            val sourcesBaselineTask = project.tasks.register(
                "manifestShieldSourcesBaseline$capitalizedName",
                ManifestSourcesDiffTask::class.java
            ) {
                dependsOn(mergedManifest)
                setParams(config, mergedManifest, blameLogProvider, project.path, project.rootDir, baselineDirectory, filePrefix, true)
            }
            perConfigBaselineTask.configure { dependsOn(sourcesBaselineTask) }
        }
    }
}
