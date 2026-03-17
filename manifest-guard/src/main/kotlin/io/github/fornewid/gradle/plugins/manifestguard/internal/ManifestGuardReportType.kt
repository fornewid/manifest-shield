package io.github.fornewid.gradle.plugins.manifestguard.internal

internal enum class ManifestGuardReportType(
    val reportTypeName: String,
    val fileSuffix: String
) {
    LIST("Manifest", ""),
    TREE("Manifest Tree", ".tree");

    val filePrefix: String = ""

    override fun toString(): String {
        return reportTypeName
    }
}
