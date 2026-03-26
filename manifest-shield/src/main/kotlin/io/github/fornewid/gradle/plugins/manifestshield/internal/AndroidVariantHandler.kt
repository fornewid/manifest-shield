package io.github.fornewid.gradle.plugins.manifestshield.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldConfiguration
import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldPluginExtension
import io.github.fornewid.gradle.plugins.manifestshield.internal.list.ManifestShieldListTask
import io.github.fornewid.gradle.plugins.manifestshield.internal.sources.ManifestSourcesDiffTask
import io.github.fornewid.gradle.plugins.manifestshield.internal.utils.OutputFileUtils
import org.gradle.api.GradleException
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

        // Track all variant names, declared config names, and which matched.
        val allVariantNames = mutableSetOf<String>()
        val declaredConfigNames = mutableSetOf<String>()
        val matchedConfigs = mutableSetOf<String>()

        androidComponents.onVariants { variant ->
            allVariantNames.add(variant.name)
            extension.configurations.configureEach {
                declaredConfigNames.add(configurationName)
                if (configurationName == variant.name) {
                    matchedConfigs.add(configurationName)
                    registerTasks(project, extension.baselineDir.get(), this, variant, guardTask, baselineTask)
                }
            }
        }

        // Validate at task configuration time (not doFirst) — CC-safe.
        // Only plain String sets are referenced — no extension or project objects.
        guardTask.configure {
            validateConfigurations(declaredConfigNames, matchedConfigs, allVariantNames)
        }
        baselineTask.configure {
            validateConfigurations(declaredConfigNames, matchedConfigs, allVariantNames)
        }
    }

    private fun validateConfigurations(
        declaredConfigNames: Set<String>,
        matchedConfigs: Set<String>,
        allVariantNames: Set<String>,
    ) {
        for (name in declaredConfigNames) {
            if (name !in matchedConfigs) {
                throw GradleException(buildString {
                    appendLine("Manifest Shield could not resolve configuration \"$name\".")
                    if (allVariantNames.isNotEmpty()) {
                        appendLine("Here are some valid configurations you could use.")
                        appendLine()
                        appendLine("manifestShield {")
                        allVariantNames.forEach { appendLine("    configuration(\"$it\")") }
                        appendLine("}")
                    }
                })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }

    /**
     * Converts a camelCase string to kebab-case.
     * AGP generates blame report files using this format.
     * e.g., "devRelease" -> "dev-release", "release" -> "release", "dev2Debug" -> "dev2-debug"
     */
    internal fun String.toKebabCase(): String {
        return replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
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
        val capitalizedName = config.configurationName.capitalize()
        val baselineDirectory = OutputFileUtils.manifestShieldDir(project, baselineDir)
        val filePrefix = "${config.configurationName}AndroidManifest"
        val blameLogProvider = project.layout.buildDirectory
            .file("outputs/logs/manifest-merger-${config.configurationName.toKebabCase()}-report.txt")

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
