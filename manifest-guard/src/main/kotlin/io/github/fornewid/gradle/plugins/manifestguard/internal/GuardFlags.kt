package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Common guard flag properties shared between ManifestGuardListTask and ManifestTreeDiffTask.
 */
internal interface GuardFlags {

    @get:Input
    val guardSdk: Property<Boolean>

    @get:Input
    val guardPermissions: Property<Boolean>

    @get:Input
    val guardPermissionDeclarations: Property<Boolean>

    @get:Input
    val guardActivities: Property<Boolean>

    @get:Input
    val guardActivityAliases: Property<Boolean>

    @get:Input
    val guardServices: Property<Boolean>

    @get:Input
    val guardReceivers: Property<Boolean>

    @get:Input
    val guardProviders: Property<Boolean>

    @get:Input
    val guardFeatures: Property<Boolean>

    @get:Input
    val guardIntentFilters: Property<Boolean>

    @get:Input
    val guardStartup: Property<Boolean>
}

internal fun GuardFlags.applyConfig(config: ManifestGuardConfiguration) {
    guardSdk.set(config.sdk)
    guardPermissions.set(config.permissions)
    guardPermissionDeclarations.set(config.permissionDeclarations)
    guardActivities.set(config.activities)
    guardActivityAliases.set(config.activityAliases)
    guardServices.set(config.services)
    guardReceivers.set(config.receivers)
    guardProviders.set(config.providers)
    guardFeatures.set(config.features)
    guardIntentFilters.set(config.intentFilters)
    guardStartup.set(config.startup)
}
