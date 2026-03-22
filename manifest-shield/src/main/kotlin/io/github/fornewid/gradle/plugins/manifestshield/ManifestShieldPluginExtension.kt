package io.github.fornewid.gradle.plugins.manifestshield

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension for [ManifestShieldPlugin] which leverages [ManifestShieldConfiguration]
 */
public open class ManifestShieldPluginExtension @Inject constructor(
    private val objects: ObjectFactory
) {
    /** Name of the directory to store baseline files (default: "manifestShield") */
    public val baselineDir: Property<String> = objects.property(String::class.java).convention("manifestShield")

    internal val configurations = objects.domainObjectContainer(ManifestShieldConfiguration::class.java)

    public fun configuration(name: String) {
        configurations.add(newConfiguration(name))
    }

    /**
     * Supports configuration in build files.
     *
     * manifestShield {
     *   baselineDir.set("custom-dir")
     *   configuration("release") {
     *     usesPermission = true
     *     activity = true
     *     sources = true
     *   }
     * }
     */
    public fun configuration(name: String, config: Action<ManifestShieldConfiguration>) {
        configurations.add(newConfiguration(name, config))
    }

    private fun newConfiguration(
        name: String,
        config: Action<ManifestShieldConfiguration>? = null
    ): ManifestShieldConfiguration {
        return objects.newInstance(ManifestShieldConfiguration::class.java, name).apply {
            config?.execute(this)
        }
    }
}
