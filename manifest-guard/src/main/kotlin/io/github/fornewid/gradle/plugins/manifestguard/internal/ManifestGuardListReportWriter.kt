package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ColorTerminal
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiff
import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult
import java.io.File

internal class ManifestGuardListReportWriter(
    private val artifacts: Boolean,
    private val modules: Boolean
) {

    /**
     * @return Whether changes were detected
     */
    internal fun writeReport(
        projectDirOutputFile: File,
        report: ManifestGuardReportData,
        shouldBaseline: Boolean,
    ): ManifestListDiffResult {
        val reportContent = report.reportForConfig(
            artifacts = artifacts,
            modules = modules
        )

        val projectDirOutputFileExists = projectDirOutputFile.exists()
        return if (shouldBaseline || !projectDirOutputFileExists) {
            projectDirOutputFile.writeText(reportContent)
            return ManifestListDiffResult.BaselineCreated(
                projectPath = report.projectPath,
                configurationName = report.configurationName,
                baselineFile = projectDirOutputFile,
            )
        } else {
            val expectedFileContent = projectDirOutputFile.readText()
            // Perform Diff
            val diffResult = ManifestListDiff.performDiff(
                projectPath = report.projectPath,
                configurationName = report.configurationName,
                expectedDependenciesFileContent = expectedFileContent,
                actualDependenciesFileContent = reportContent
            )
            diffResult
        }
    }
}
