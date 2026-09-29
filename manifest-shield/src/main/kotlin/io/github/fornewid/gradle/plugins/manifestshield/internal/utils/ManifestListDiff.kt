package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

internal object ManifestListDiff {

    fun performDiff(
        projectPath: String,
        configurationName: String,
        category: String,
        expectedContent: String,
        actualContent: String,
    ): ManifestListDiffResult.DiffPerformed {
        val removedAndAddedLines: RemovedAndAddedLines = RemovedAndAddedLines(
            diffLines = diff(expected = parse(expectedContent), actual = parse(actualContent))
        )

        return if (removedAndAddedLines.hasDifference) {
            ManifestListDiffResult.DiffPerformed.HasDiff(
                projectPath = projectPath,
                configurationName = configurationName,
                category = category,
                removedAndAddedLines = removedAndAddedLines,
            )
        } else {
            ManifestListDiffResult.DiffPerformed.NoDiff(
                projectPath = projectPath,
                configurationName = configurationName,
                category = category,
            )
        }
    }

    /**
     * A baseline line and the lines nested under it by indentation.
     * Unindented lines are headers: `[source]` groups (sources baseline) and `tag:` sections.
     * Indented lines are entries, compared as whole subtrees.
     */
    private class Node(val line: String, val indent: Int) {
        val children = mutableListOf<Node>()
        val isHeader: Boolean get() = indent <= 0

        /** Sibling order is ignored at every level, so only placement and multiplicity matter. */
        val canonical: String by lazy {
            line.trim() + children.map { it.canonical }.sorted().joinToString("") { "\n" + it.prependIndent("  ") }
        }

        fun diffLines(type: DiffLine.Type): List<DiffLine> =
            listOf(DiffLine(type, line)) + children.flatMap { it.diffLines(type) }
    }

    /**
     * Headers are matched by text; the `[` rule mirrors the source group lines written by
     * [SourcesContentBuilder.buildMergedWithSdk], which share column 0 with `tag:` sections.
     */
    private fun parse(content: String): Node {
        val root = Node(line = "", indent = -2)
        val stack = ArrayDeque<Node>().apply { add(root) }
        for (line in content.lines()) {
            if (line.isBlank()) continue
            val indent = if (line.startsWith("[")) -1 else line.length - line.trimStart().length
            while (stack.last().indent >= indent) stack.removeLast()
            val node = Node(line, indent)
            stack.last().children.add(node)
            stack.add(node)
        }
        return root
    }

    private fun diff(expected: Node, actual: Node): List<DiffLine> {
        val result = mutableListOf<DiffLine>()
        val (expectedHeaders, expectedEntries) = expected.children.partition { it.isHeader }
        val (actualHeaders, actualEntries) = actual.children.partition { it.isHeader }

        val expectedByLine = expectedHeaders.associateBy { it.line }
        val actualByLine = actualHeaders.associateBy { it.line }
        for (line in mergeOrder(expectedByLine.keys.toList(), actualByLine.keys.toList())) {
            val e = expectedByLine[line]
            val a = actualByLine[line]
            when {
                e == null -> result.addAll(a!!.diffLines(DiffLine.Type.ADDED))
                a == null -> result.addAll(e.diffLines(DiffLine.Type.REMOVED))
                else -> {
                    val nested = diff(e, a)
                    if (nested.isNotEmpty()) {
                        result.add(DiffLine(DiffLine.Type.CONTEXT, line))
                        result.addAll(nested)
                    }
                }
            }
        }

        val removed = expectedEntries.toMutableList()
        val added = mutableListOf<Node>()
        for (node in actualEntries) {
            val match = removed.indexOfFirst { it.canonical == node.canonical }
            if (match >= 0) removed.removeAt(match) else added.add(node)
        }
        (removed.map { DiffLine.Type.REMOVED to it } + added.map { DiffLine.Type.ADDED to it })
            .sortedWith(compareBy({ it.second.line }, { it.first != DiffLine.Type.REMOVED }))
            .forEach { (type, node) -> result.addAll(node.diffLines(type)) }
        return result
    }

    /** Actual order, with headers that exist only in the baseline kept after their baseline predecessor. */
    private fun mergeOrder(expected: List<String>, actual: List<String>): List<String> {
        val result = actual.toMutableList()
        var insertAt = 0
        for (line in expected) {
            val index = result.indexOf(line)
            if (index >= 0) {
                insertAt = index + 1
            } else {
                result.add(insertAt++, line)
            }
        }
        return result
    }
}
