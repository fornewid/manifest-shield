package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

internal object ManifestListDiff {

    fun performDiff(
        projectPath: String,
        variantName: String,
        category: String,
        expectedContent: String,
        actualContent: String,
    ): ManifestListDiffResult.DiffPerformed {
        val removedAndAddedLines: RemovedAndAddedLines = compareAndAddPlusMinusPrefixes(
            expected = expectedContent.lines(),
            actual = actualContent.lines()
        )

        return if (removedAndAddedLines.hasDifference) {
            ManifestListDiffResult.DiffPerformed.HasDiff(
                projectPath = projectPath,
                variantName = variantName,
                category = category,
                removedAndAddedLines = removedAndAddedLines,
            )
        } else {
            ManifestListDiffResult.DiffPerformed.NoDiff(
                projectPath = projectPath,
                variantName = variantName,
                category = category,
            )
        }
    }

    private fun compareAndAddPlusMinusPrefixes(
        expected: List<String>,
        actual: List<String>
    ): RemovedAndAddedLines {
        val removedLines = expected.filter { !actual.contains(it) }
        val addedLines = actual.filter { !expected.contains(it) }
        return RemovedAndAddedLines(
            removedLines = removedLines,
            addedLines = addedLines,
        )
    }
}
