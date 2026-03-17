package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import java.io.File

internal sealed class ManifestListDiffResult {
    internal class BaselineCreated(
        projectPath: String,
        variantName: String,
        category: String,
        baselineFile: File,
    ) : ManifestListDiffResult() {

        private val baselineMessage = """
            Manifest Guard baseline created for $projectPath ($variantName/$category).
            File: file://${baselineFile.canonicalPath}
        """.trimIndent()

        fun baselineCreatedMessage(withColor: Boolean): String = if (withColor) {
            ColorTerminal.colorify(ColorTerminal.ANSI_YELLOW, baselineMessage)
        } else {
            baselineMessage
        }
    }

    internal sealed class DiffPerformed : ManifestListDiffResult() {

        internal class NoDiff(
            projectPath: String,
            variantName: String,
            category: String,
        ) : DiffPerformed() {
            val noDiffMessage: String =
                "No Manifest Changes Found in $projectPath for $variantName/$category"
        }

        internal data class HasDiff(
            val projectPath: String,
            val variantName: String,
            val category: String,
            val removedAndAddedLines: RemovedAndAddedLines,
        ) : DiffPerformed() {

            private val changedMessage =
                """Manifest Changed in $projectPath for $variantName/$category"""

            fun createDiffMessage(withColor: Boolean, rebaselineMessage: String): String = buildString {
                appendLine(
                    if (withColor) {
                        ColorTerminal.colorify(ColorTerminal.ANSI_YELLOW, changedMessage)
                    } else {
                        changedMessage
                    }
                )
                appendLine(
                    if (withColor) {
                        removedAndAddedLines.diffTextWithPlusAndMinusWithColor
                    } else {
                        removedAndAddedLines.diffTextWithPlusAndMinus
                    }
                )
                appendLine(
                    if (withColor) {
                        ColorTerminal.colorify(ColorTerminal.ANSI_RED, rebaselineMessage)
                    } else {
                        rebaselineMessage
                    }
                )
            }
        }
    }
}
