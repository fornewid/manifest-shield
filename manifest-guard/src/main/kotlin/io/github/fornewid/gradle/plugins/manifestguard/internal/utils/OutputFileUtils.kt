package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import io.github.fornewid.gradle.plugins.manifestguard.internal.ManifestGuardReportType
import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.Directory

internal object OutputFileUtils {

    fun projectDirDependenciesDir(
        project: Project,
    ): Directory {
        val dependenciesDir = project.layout.projectDirectory
            .dir("dependencies")
        dependenciesDir
            .asFile
            .apply {
                if (!exists()) {
                    // Create the directory if it does not exist
                    mkdirs()
                }
            }
        return dependenciesDir
    }

    fun buildDirOutputFile(
        buildDirectory: Directory,
        configurationName: String,
        reportType: ManifestGuardReportType,
    ): File {
        val configurationNameAndSuffix = "$configurationName${reportType.fileSuffix}"
        return buildDirectory
            .file("$configurationNameAndSuffix.txt")
            .asFile
            .apply {
                parentFile.apply {
                    if (!exists()) {
                        // Create the directory if it does not exist
                        mkdirs()
                    }
                }
            }
    }

    fun projectDirOutputFile(
        projectDirectory: Directory,
        configurationName: String,
        reportType: ManifestGuardReportType
    ): File {
        val configurationNameAndSuffix = "${reportType.filePrefix}$configurationName${reportType.fileSuffix}"
        return projectDirectory
            .file("$configurationNameAndSuffix.txt")
            .asFile
            .apply {
                parentFile.apply {
                    if (!exists()) {
                        // Create the directory if it does not exist
                        mkdirs()
                    }
                }
            }
    }
}
