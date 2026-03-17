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

    /** Guard uses-permission declarations */
    @get:Input
    public var permissions: Boolean = true

    /** Guard activity declarations */
    @get:Input
    public var activities: Boolean = true

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

    /** Enable tree format with library attribution from blame log */
    @get:Input
    public var tree: Boolean = false

    /** Filter to determine if a manifest entry is allowed */
    @get:Input
    public var allowedFilter: (entryName: String) -> Boolean = { true }

    /** Transform or remove (by returning null) entries from the baseline */
    @get:Input
    public var baselineMap: (entryName: String) -> String? = { it }
}
