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
    /** Name of the directory to store baseline files (default: "manifest") */
    public var baselineDir: String = "manifestGuard"

    internal val configurations = objects.domainObjectContainer(ManifestGuardConfiguration::class.java)

    public fun configuration(name: String) {
        configurations.add(newConfiguration(name))
    }

    /**
     * Supports configuration in build files.
     *
     * manifestGuard {
     *   configuration("release") {
     *     permissions = true
     *     activities = true
     *     tree = true
     *   }
     * }
     */
    public fun configuration(name: String, config: Action<ManifestGuardConfiguration>) {
        configurations.add(newConfiguration(name, config))
    }

    private fun newConfiguration(
        name: String,
        config: Action<ManifestGuardConfiguration>? = null
    ): ManifestGuardConfiguration {
        return objects.newInstance(ManifestGuardConfiguration::class.java, name).apply {
            config?.execute(this)
        }
    }
}
