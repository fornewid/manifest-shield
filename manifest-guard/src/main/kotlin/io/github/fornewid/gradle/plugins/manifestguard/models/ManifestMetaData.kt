package io.github.fornewid.gradle.plugins.manifestguard.models

internal data class ManifestMetaData(
    override val name: String,
    val value: String?,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        if (value != null) {
            val displayValue = when {
                value == "true" || value == "false" -> value
                value.toIntOrNull() != null -> value
                else -> "REDACTED"
            }
            append(" ($displayValue)")
        }
    }
}
