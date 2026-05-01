package io.github.fornewid.gradle.plugins.manifestshield.models

internal data class ManifestSdk(
    val minSdkVersion: String?,
    val targetSdkVersion: String?,
) {
    fun toBaselineLines(): List<String> = buildList {
        minSdkVersion?.let { add("minSdkVersion=$it") }
        targetSdkVersion?.let { add("targetSdkVersion=$it") }
    }
}
