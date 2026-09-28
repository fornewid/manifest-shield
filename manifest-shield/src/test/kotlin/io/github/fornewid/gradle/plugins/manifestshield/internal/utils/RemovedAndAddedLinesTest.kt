package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class RemovedAndAddedLinesTest {

    @Test
    fun `no difference when there are no lines`() {
        val result = RemovedAndAddedLines(diffLines = emptyList())

        assertThat(result.hasDifference).isFalse()
    }

    @Test
    fun `has difference when lines are removed`() {
        val result = RemovedAndAddedLines(
            diffLines = listOf(DiffLine(DiffLine.Type.REMOVED, "android.permission.CAMERA"))
        )

        assertThat(result.hasDifference).isTrue()
        assertThat(result.diffTextWithPlusAndMinus).contains("- android.permission.CAMERA")
    }

    @Test
    fun `has difference when lines are added`() {
        val result = RemovedAndAddedLines(
            diffLines = listOf(DiffLine(DiffLine.Type.ADDED, "android.permission.INTERNET"))
        )

        assertThat(result.hasDifference).isTrue()
        assertThat(result.diffTextWithPlusAndMinus).contains("+ android.permission.INTERNET")
    }

    @Test
    fun `diff text keeps line order and prints context lines without prefix`() {
        val result = RemovedAndAddedLines(
            diffLines = listOf(
                DiffLine(DiffLine.Type.CONTEXT, "uses-permission:"),
                DiffLine(DiffLine.Type.REMOVED, "  z.permission"),
                DiffLine(DiffLine.Type.ADDED, "  a.permission"),
            )
        )

        assertThat(result.diffTextWithPlusAndMinus).isEqualTo(
            "  uses-permission:\n-   z.permission\n+   a.permission\n"
        )
    }

    @Test
    fun `colored output contains ANSI codes`() {
        val result = RemovedAndAddedLines(
            diffLines = listOf(
                DiffLine(DiffLine.Type.REMOVED, "removed"),
                DiffLine(DiffLine.Type.ADDED, "added"),
            )
        )

        assertThat(result.diffTextWithPlusAndMinusWithColor).contains("\u001B[")
    }
}
