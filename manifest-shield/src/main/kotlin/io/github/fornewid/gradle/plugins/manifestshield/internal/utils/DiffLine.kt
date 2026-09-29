package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

internal data class DiffLine(val type: Type, val str: String) {
    enum class Type(val prefix: String, val color: String?) {
        CONTEXT("  ", null),
        REMOVED("- ", ColorTerminal.ANSI_RED),
        ADDED("+ ", ColorTerminal.ANSI_GREEN),
    }
}
