package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.Directory

internal object OutputFileUtils {

    fun manifestGuardDir(
        project: Project,
        baselineDir: String,
        configurationName: String,
    ): Directory {
        val dir = project.layout.projectDirectory
            .dir(baselineDir)
            .dir(configurationName)
        dir.asFile.apply {
            if (!exists()) mkdirs()
        }
        return dir
    }

    fun baselineFile(
        directory: Directory,
        fileName: String,
    ): File {
        return directory
            .file("$fileName.txt")
            .asFile
            .apply {
                parentFile.apply {
                    if (!exists()) mkdirs()
                }
            }
    }
}
