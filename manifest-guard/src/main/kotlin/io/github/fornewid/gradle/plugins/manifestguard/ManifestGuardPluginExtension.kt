package io.github.fornewid.gradle.plugins.manifestguard

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Extension for [ManifestGuardPlugin] which leverages [ManifestGuardConfiguration]
 */
public open class ManifestGuardPluginExtension @Inject constructor(
    private val objects: ObjectFactory
) {
    internal val configurations = objects.domainObjectContainer(ManifestGuardConfiguration::class.java)

    public fun configuration(name: String) {
        configurations.add(newConfiguration(name))
    }

    /**
     * Supports configuration in build files.
     *
     * manifestGuard {
     *   configuration("normalReleaseRuntimeClasspath") {
     *     artifactReport = true
     *     moduleReport = false
     *   }
     * }
     */
    public fun configuration(name: String, config: Action<ManifestGuardConfiguration>) {
        configurations.add(newConfiguration(name, config))
    }

    // We cannot use `configurations.create(name, config)` because the config block is executed
    // after the `all {}` block in the plugin. This is silly, but a reasonable workaround.
    private fun newConfiguration(
        name: String,
        config: Action<ManifestGuardConfiguration>? = null
    ): ManifestGuardConfiguration {
        return objects.newInstance(ManifestGuardConfiguration::class.java, name).apply {
            // execute the config block immediately, then add it to the container
            config?.execute(this)
        }
    }
}
