package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

internal data class RemovedAndAddedLines(
    val diffLines: List<DiffLine>,
) {
    val hasDifference = diffLines.isNotEmpty()

    val diffTextWithPlusAndMinus: String = diffLines.fold(StringBuilder()) { builder, it ->
        builder.appendLine(it.type.prefix + it.str)
    }.toString()

    val diffTextWithPlusAndMinusWithColor: String = diffLines.fold(StringBuilder()) { builder, it ->
        builder.appendLine(ColorTerminal.colorify(it.type.color, it.type.prefix + it.str))
    }.toString()
}
