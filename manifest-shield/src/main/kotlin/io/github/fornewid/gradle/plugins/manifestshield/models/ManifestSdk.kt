package io.github.fornewid.gradle.plugins.manifestshield.models

internal data class ManifestSdk(
    val minSdkVersion: String?,
    val targetSdkVersion: String?,
) {
    // `buildList` (Kotlin 1.6+) is intentionally avoided; project targets language version 1.4.
    fun toBaselineLines(): List<String> = listOfNotNull(
        minSdkVersion?.let { "minSdkVersion=$it" },
        targetSdkVersion?.let { "targetSdkVersion=$it" },
    )
}
