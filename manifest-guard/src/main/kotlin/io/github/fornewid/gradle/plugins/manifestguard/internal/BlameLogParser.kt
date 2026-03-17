package io.github.fornewid.gradle.plugins.manifestguard.internal

import java.io.File

internal data class BlameEntry(
    val elementType: String,
    val elementName: String,
    val source: String,
)

internal object BlameLogParser {

    private val ELEMENT_LINE = Regex("""^([\w-]+)#(.+)$""")
    private val ADDED_FROM_LIBRARY = Regex("""\s*ADDED from \[(.+?)]""")

    fun parse(blameLogFile: File): List<BlameEntry> {
        if (!blameLogFile.exists()) return emptyList()

        val entries = mutableListOf<BlameEntry>()
        var currentElementType: String? = null
        var currentElementName: String? = null

        for (line in blameLogFile.readLines()) {
            val elementMatch = ELEMENT_LINE.matchEntire(line.trim())
            if (elementMatch != null) {
                currentElementType = elementMatch.groupValues[1]
                currentElementName = elementMatch.groupValues[2]
                continue
            }

            if (currentElementType != null && currentElementName != null) {
                val libraryMatch = ADDED_FROM_LIBRARY.find(line)
                if (libraryMatch != null) {
                    entries.add(
                        BlameEntry(
                            elementType = currentElementType,
                            elementName = currentElementName,
                            source = libraryMatch.groupValues[1],
                        )
                    )
                    currentElementType = null
                    currentElementName = null
                } else if (line.trimStart().startsWith("ADDED from")) {
                    entries.add(
                        BlameEntry(
                            elementType = currentElementType,
                            elementName = currentElementName,
                            source = "app",
                        )
                    )
                    currentElementType = null
                    currentElementName = null
                }
            }
        }
        return entries
    }

    fun buildSourceMap(entries: List<BlameEntry>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (entry in entries) {
            val key = "${entry.elementType}#${entry.elementName}"
            map[key] = entry.source
        }
        return map
    }
}
