package io.github.fornewid.gradle.plugins.manifestguard.internal

import java.io.File

internal data class BlameEntry(
    val elementType: String,
    val elementName: String,
    val source: String,
)

internal object BlameLogParser {

    // Matches element lines like "uses-permission#android.permission.INTERNET"
    // Also matches elements without a name like "uses-sdk" or "application"
    private val ELEMENT_WITH_NAME = Regex("""^([\w-]+)#(.+)$""")
    private val ELEMENT_WITHOUT_NAME = Regex("""^([\w-]+)$""")

    // Matches action lines: ADDED/INJECTED from [source] /path or ADDED/INJECTED from /path
    private val ACTION_FROM_BRACKETED = Regex("""\s*(?:ADDED|INJECTED) from \[(.+?)] (/\S+)""")
    private val ACTION_FROM_PATH = Regex("""\s*(?:ADDED|INJECTED) from (/\S+?)(?::\d|$| reason:)""")

    fun parse(blameLogFile: File, projectDir: File? = null): List<BlameEntry> {
        if (!blameLogFile.exists()) return emptyList()

        val entries = mutableListOf<BlameEntry>()
        var currentElementType: String? = null
        var currentElementName: String? = null

        for (line in blameLogFile.readLines()) {
            val trimmed = line.trim()

            // Skip attribute lines (indented with tabs)
            if (line.startsWith("\t")) continue

            // Try to match element line with name: "uses-permission#android.permission.INTERNET"
            val nameMatch = ELEMENT_WITH_NAME.matchEntire(trimmed)
            if (nameMatch != null) {
                currentElementType = nameMatch.groupValues[1]
                currentElementName = nameMatch.groupValues[2]
                continue
            }

            // Try to match element line without name: "uses-sdk", "application", "manifest"
            val noNameMatch = ELEMENT_WITHOUT_NAME.matchEntire(trimmed)
            if (noNameMatch != null) {
                currentElementType = noNameMatch.groupValues[1]
                currentElementName = null
                continue
            }

            // Try to match action lines for current element
            if (currentElementType != null) {
                // First match: ADDED/INJECTED from [library] /path
                val bracketedMatch = ACTION_FROM_BRACKETED.find(line)
                if (bracketedMatch != null) {
                    val source = bracketedMatch.groupValues[1]
                    if (currentElementName != null) {
                        entries.add(BlameEntry(currentElementType, currentElementName, source))
                    }
                    currentElementType = null
                    currentElementName = null
                    continue
                }

                // Second match: ADDED/INJECTED from /path (no brackets = app module)
                val pathMatch = ACTION_FROM_PATH.find(line)
                if (pathMatch != null) {
                    val filePath = pathMatch.groupValues[1]
                    val source = resolveModuleSource(filePath, projectDir)
                    if (currentElementName != null) {
                        entries.add(BlameEntry(currentElementType, currentElementName, source))
                    }
                    currentElementType = null
                    currentElementName = null
                    continue
                }
            }
        }
        return entries
    }

    /**
     * Resolve the module source from a file path.
     * Extracts the module path relative to the project root directory.
     *
     * Example: "/Users/.../MyProject/app/src/main/AndroidManifest.xml" with projectDir "/Users/.../MyProject"
     * → ":app"
     *
     * Example: "/Users/.../MyProject/feature/core/src/main/AndroidManifest.xml"
     * → ":feature:core"
     */
    private fun resolveModuleSource(filePath: String, projectDir: File?): String {
        if (projectDir == null) return filePath

        val rootPath = projectDir.absolutePath
        if (!filePath.startsWith(rootPath)) return filePath

        val relativePath = filePath.removePrefix(rootPath).removePrefix("/")
        // Extract module path: everything before "/src/" or "/build/"
        val modulePath = relativePath.split("/src/", "/build/").firstOrNull() ?: return filePath
        return if (modulePath.isEmpty()) {
            ":"
        } else {
            ":${modulePath.replace("/", ":")}"
        }
    }

    fun buildSourceMap(entries: List<BlameEntry>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (entry in entries) {
            val key = "${entry.elementType}#${entry.elementName}"
            // First entry wins (ADDED takes priority over later MERGED)
            if (key !in map) {
                map[key] = entry.source
            }
        }
        return map
    }
}
