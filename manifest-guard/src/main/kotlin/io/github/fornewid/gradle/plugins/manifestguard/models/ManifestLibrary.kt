package io.github.fornewid.gradle.plugins.manifestguard.models

internal data class ManifestLibrary(
    override val name: String,
    val required: Boolean,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        if (required) append(" (required)")
    }
}
