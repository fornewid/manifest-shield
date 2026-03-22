package io.github.fornewid.gradle.plugins.manifestguard

import org.gradle.api.Named
import org.gradle.api.tasks.Input
import javax.inject.Inject

/**
 * Configuration for [ManifestGuardPlugin] per build variant.
 */
public open class ManifestGuardConfiguration @Inject constructor(
    /**
     * Name of the build variant (e.g., "release", "debug", "devRelease")
     */
    @get:Input
    public val configurationName: String,
) : Named {

    @Input
    public override fun getName(): String = configurationName

    /** Guard uses-sdk declarations (minSdkVersion, targetSdkVersion) */
    @get:Input
    public var sdk: Boolean = true

    /** Guard uses-permission declarations */
    @get:Input
    public var permissions: Boolean = true

    /** Guard permission declarations */
    @get:Input
    public var permissionDeclarations: Boolean = true

    /** Guard activity declarations */
    @get:Input
    public var activities: Boolean = true

    /** Guard activity-alias declarations */
    @get:Input
    public var activityAliases: Boolean = true

    /** Guard service declarations */
    @get:Input
    public var services: Boolean = true

    /** Guard receiver declarations */
    @get:Input
    public var receivers: Boolean = true

    /** Guard provider declarations */
    @get:Input
    public var providers: Boolean = true

    /** Guard uses-feature declarations */
    @get:Input
    public var features: Boolean = true

    /** Guard intent-filter declarations on exported components */
    @get:Input
    public var intentFilters: Boolean = true

    /** Guard androidx.startup initializer declarations */
    @get:Input
    public var startup: Boolean = true

    /** Guard uses-permission-sdk-23 declarations */
    @get:Input
    public var permissionsSdk23: Boolean = false

    /** Guard supports-screens declarations */
    @get:Input
    public var supportsScreens: Boolean = false

    /** Guard compatible-screens declarations */
    @get:Input
    public var compatibleScreens: Boolean = false

    /** Guard uses-configuration declarations */
    @get:Input
    public var usesConfiguration: Boolean = false

    /** Guard supports-gl-texture declarations */
    @get:Input
    public var supportsGlTexture: Boolean = false

    /** Guard queries declarations */
    @get:Input
    public var queries: Boolean = false

    /** Guard meta-data declarations (values redacted for non-primitives) */
    @get:Input
    public var metaData: Boolean = false

    /** Guard uses-library declarations */
    @get:Input
    public var usesLibrary: Boolean = false

    /** Guard uses-native-library declarations */
    @get:Input
    public var usesNativeLibrary: Boolean = false

    /** Guard profileable declarations */
    @get:Input
    public var profileable: Boolean = false

    /** Enable source-attributed format grouped by library/module origin */
    @get:Input
    public var sources: Boolean = false

    /** Filter to determine if a manifest entry is allowed */
    @get:Input
    public var allowedFilter: (entryName: String) -> Boolean = { true }

    /** Transform or remove (by returning null) entries from the baseline */
    @get:Input
    public var baselineMap: (entryName: String) -> String? = { it }
}
