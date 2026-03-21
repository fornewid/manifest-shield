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
    val guardPermissionsSdk23: Property<Boolean>

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

    @get:Input
    val guardSupportsScreens: Property<Boolean>

    @get:Input
    val guardCompatibleScreens: Property<Boolean>

    @get:Input
    val guardUsesConfiguration: Property<Boolean>

    @get:Input
    val guardSupportsGlTexture: Property<Boolean>

    @get:Input
    val guardQueries: Property<Boolean>

    @get:Input
    val guardMetaData: Property<Boolean>

    @get:Input
    val guardUsesLibrary: Property<Boolean>

    @get:Input
    val guardUsesNativeLibrary: Property<Boolean>

    @get:Input
    val guardProfileable: Property<Boolean>
}

internal fun GuardFlags.applyConfig(config: ManifestGuardConfiguration) {
    guardSdk.set(config.sdk)
    guardPermissions.set(config.permissions)
    guardPermissionsSdk23.set(config.permissionsSdk23)
    guardPermissionDeclarations.set(config.permissionDeclarations)
    guardActivities.set(config.activities)
    guardActivityAliases.set(config.activityAliases)
    guardServices.set(config.services)
    guardReceivers.set(config.receivers)
    guardProviders.set(config.providers)
    guardFeatures.set(config.features)
    guardIntentFilters.set(config.intentFilters)
    guardStartup.set(config.startup)
    guardSupportsScreens.set(config.supportsScreens)
    guardCompatibleScreens.set(config.compatibleScreens)
    guardUsesConfiguration.set(config.usesConfiguration)
    guardSupportsGlTexture.set(config.supportsGlTexture)
    guardQueries.set(config.queries)
    guardMetaData.set(config.metaData)
    guardUsesLibrary.set(config.usesLibrary)
    guardUsesNativeLibrary.set(config.usesNativeLibrary)
    guardProfileable.set(config.profileable)
}
