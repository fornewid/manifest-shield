package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.internal.utils.ManifestListDiffResult
import io.github.fornewid.gradle.plugins.manifestguard.models.ArtifactManifest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

import java.io.File

internal class ManifestGuardReportDataTest {
    @Test
    fun `new library is added`() {
        TestDelegate().apply {

            // Baseline Sample Report
            val report1 = SAMPLE_REPORT
            whenReportWritten(
                report = report1,
                shouldBaseline = true,
            )

            val report2 = report1.copy(
                dependencies = report1.dependencies.toMutableList().apply {
                    add(
                        ArtifactManifest(
                            group = "io.github.fornewid",
                            artifact = "focus",
                            version = "1.0.0",
                        )
                    )
                },
            )

            val result: ManifestListDiffResult = whenReportWritten(
                report = report2,
                shouldBaseline = false,
            )

            assertThat(report2.reportForConfig())
                .isNotEqualTo(report1.reportForConfig())
            assertThat(report2.reportForConfig().lines().size)
                .isEqualTo(report1.reportForConfig().lines().size + 1)

            when (result) {
                is ManifestListDiffResult.DiffPerformed.HasDiff -> {
                    assertThat(result.removedAndAddedLines.diffTextWithPlusAndMinus)
                        .contains("+ io.github.fornewid:focus:1.0.0")
                }
                else -> fail("Did not expect $result")
            }
        }
    }

    @Test
    fun `version upgrade of existing library`() {
        TestDelegate().apply {
            val report1 = SAMPLE_REPORT.copy(
                dependencies = listOf(
                    ArtifactManifest(
                        group = "io.github.fornewid",
                        artifact = "focus",
                        version = "1.0.0",
                    )
                )
            )
            // Baselines
            whenReportWritten(report = report1, shouldBaseline = true)
            val report2 = SAMPLE_REPORT.copy(
                dependencies = listOf(
                    ArtifactManifest(
                        group = "io.github.fornewid",
                        artifact = "focus",
                        version = "1.1.0",
                    )
                )
            )
            // Should cause error with the difference in versions
            val result = whenReportWritten(report = report2, shouldBaseline = false)

            assertThat(report2.reportForConfig())
                .isNotEqualTo(report1.reportForConfig())
            assertThat(report2.reportForConfig().lines().size)
                .isEqualTo(report1.reportForConfig().lines().size)

            when (result) {
                is ManifestListDiffResult.DiffPerformed.HasDiff -> {
                    val diffText = result.removedAndAddedLines.diffTextWithPlusAndMinus
                    assertThat(diffText)
                        .contains("- io.github.fornewid:focus:1.0.0")
                    assertThat(diffText)
                        .contains("+ io.github.fornewid:focus:1.1.0")
                }
                else -> fail("Did not expect $result")
            }
        }
    }

    @Test
    fun `test baselineMap modify`() {
        val simpleReport = SAMPLE_REPORT.copy(
            dependencies = listOf(
                ArtifactManifest(
                    group = "group",
                    artifact = "artifact",
                    version = "1"
                )
            ),
            baselineMap = { "$it-extra" }
        )

        assertThat(simpleReport.reportForConfig())
            .contains("group:artifact:1-extra")
    }

    @Test
    fun `test baselineMap no-op`() {
        val simpleReport = SAMPLE_REPORT.copy(
            dependencies = listOf(
                ArtifactManifest(
                    group = "group",
                    artifact = "artifact",
                    version = "1"
                )
            )
        )
        assertThat(simpleReport.reportForConfig())
            .contains("group:artifact:1")
    }

    @Test
    fun `test baselineMap remove`() {
        val simpleReport = SAMPLE_REPORT.copy(
            dependencies = listOf(
                ArtifactManifest(
                    group = "group",
                    artifact = "artifact",
                    version = "1"
                )
            ),
            baselineMap = { null },
        )
        assertThat(simpleReport.reportForConfig())
            .isEmpty()
    }

    private class TestDelegate {
        private val projectDirOutputFile = File.createTempFile("projectDir", ".txt")
        private val reportWriter = ManifestGuardListReportWriter(
            artifacts = true,
            modules = true,
        )

        fun whenReportWritten(report: ManifestGuardReportData, shouldBaseline: Boolean): ManifestListDiffResult {
            return reportWriter.writeReport(
                projectDirOutputFile = projectDirOutputFile,
                report = report,
                shouldBaseline = shouldBaseline,
            )
        }
    }

    companion object {
        const val PROJECT_PATH = "project-path"
        const val CONFIGURATION_NAME = "configuration-name"
        val SAMPLE_REPORT = ManifestGuardReportData(
            projectPath = PROJECT_PATH,
            configurationName = CONFIGURATION_NAME,
            allowedFilter = { true },
            baselineMap = { it },
            dependencies = listOf()
        )
    }
}
