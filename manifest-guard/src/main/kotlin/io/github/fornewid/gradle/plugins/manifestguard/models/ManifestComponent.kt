package io.github.fornewid.gradle.plugins.manifestguard.models

internal data class ManifestComponent(
    override val name: String,
    val type: ComponentType,
    val exported: Boolean?,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        if (exported == true) {
            append(" (exported)")
        }
    }
}

internal enum class ComponentType(val tagName: String) {
    ACTIVITY("activity"),
    SERVICE("service"),
    RECEIVER("receiver"),
    PROVIDER("provider"),
}
