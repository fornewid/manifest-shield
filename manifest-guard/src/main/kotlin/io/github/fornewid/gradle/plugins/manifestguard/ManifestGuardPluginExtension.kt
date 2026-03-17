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
    internal val variants = objects.domainObjectContainer(ManifestGuardConfiguration::class.java)

    public fun variant(name: String) {
        variants.add(newVariantConfig(name))
    }

    /**
     * Supports configuration in build files.
     *
     * manifestGuard {
     *   variant("release") {
     *     permissions = true
     *     activities = true
     *     tree = true
     *   }
     * }
     */
    public fun variant(name: String, config: Action<ManifestGuardConfiguration>) {
        variants.add(newVariantConfig(name, config))
    }

    private fun newVariantConfig(
        name: String,
        config: Action<ManifestGuardConfiguration>? = null
    ): ManifestGuardConfiguration {
        return objects.newInstance(ManifestGuardConfiguration::class.java, name).apply {
            config?.execute(this)
        }
    }
}
