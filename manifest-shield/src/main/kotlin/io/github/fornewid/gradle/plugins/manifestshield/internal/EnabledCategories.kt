package io.github.fornewid.gradle.plugins.manifestshield.internal

import io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldConfiguration

internal data class EnabledCategories(
    val sdk: Boolean,
    val features: Boolean,
    val permissions: Boolean,
    val permissionsSdk23: Boolean,
    val permissionDeclarations: Boolean,
    val supportsScreens: Boolean,
    val compatibleScreens: Boolean,
    val usesConfiguration: Boolean,
    val supportsGlTexture: Boolean,
    val queries: Boolean,
    val activities: Boolean,
    val activityAliases: Boolean,
    val metaData: Boolean,
    val services: Boolean,
    val receivers: Boolean,
    val providers: Boolean,
    val usesLibrary: Boolean,
    val usesNativeLibrary: Boolean,
    val profileable: Boolean,
    val intentFilters: Boolean,
    val startup: Boolean,
) {
    companion object {
        fun from(flags: ShieldFlags): EnabledCategories = EnabledCategories(
            sdk = flags.guardSdk.get(),
            features = flags.guardFeatures.get(),
            permissions = flags.guardPermissions.get(),
            permissionsSdk23 = flags.guardPermissionsSdk23.get(),
            permissionDeclarations = flags.guardPermissionDeclarations.get(),
            supportsScreens = flags.guardSupportsScreens.get(),
            compatibleScreens = flags.guardCompatibleScreens.get(),
            usesConfiguration = flags.guardUsesConfiguration.get(),
            supportsGlTexture = flags.guardSupportsGlTexture.get(),
            queries = flags.guardQueries.get(),
            activities = flags.guardActivities.get(),
            activityAliases = flags.guardActivityAliases.get(),
            metaData = flags.guardMetaData.get(),
            services = flags.guardServices.get(),
            receivers = flags.guardReceivers.get(),
            providers = flags.guardProviders.get(),
            usesLibrary = flags.guardUsesLibrary.get(),
            usesNativeLibrary = flags.guardUsesNativeLibrary.get(),
            profileable = flags.guardProfileable.get(),
            intentFilters = flags.guardIntentFilters.get(),
            startup = flags.guardStartup.get(),
        )
    }
}
