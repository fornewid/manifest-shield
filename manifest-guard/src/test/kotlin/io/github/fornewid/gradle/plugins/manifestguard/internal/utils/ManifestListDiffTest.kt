package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class ManifestListDiffTest {

    @Test
    fun performDiffTestRootProject() {

        val result: ManifestListDiffResult.DiffPerformed = ManifestListDiff.performDiff(
            projectPath = ":",
            configurationName = "classpath",
            expectedDependenciesFileContent = """
            :sample:module2
            androidx.activity:activity:1.3.1
            """.trimIndent(),
            actualDependenciesFileContent = """
            :sample:module2
            androidx.activity:activity:1.4.0
            """.trimIndent()
        )

        when (result) {
            is ManifestListDiffResult.DiffPerformed.HasDiff -> {
                val actual = result.createDiffMessage(false)
                val expected = """
                    Dependencies Changed in : for configuration classpath
                    - androidx.activity:activity:1.3.1
                    + androidx.activity:activity:1.4.0
                    
                    If this is intentional, re-baseline using ./gradlew :manifestGuardBaseline
                    Or use ./gradlew manifestGuardBaseline to re-baseline dependencies in entire project.
                    
                    """.trimIndent()

                assertThat(actual)
                    .isEqualTo(expected)
            }
            else -> fail("Invalid Result: $result")
        }
    }

    @Test
    fun performDiffTestModule() {

        val result: ManifestListDiffResult.DiffPerformed = ManifestListDiff.performDiff(
            projectPath = ":sample:app",
            configurationName = "classpath",
            expectedDependenciesFileContent = """
            :sample:module2
            androidx.activity:activity:1.3.1
            """.trimIndent(),
            actualDependenciesFileContent = """
            :sample:module2
            androidx.activity:activity:1.4.0
            """.trimIndent()
        )

        when (result) {
            is ManifestListDiffResult.DiffPerformed.HasDiff -> {
                val actual = result.createDiffMessage(false)
                val expected = """
                    Dependencies Changed in :sample:app for configuration classpath
                    - androidx.activity:activity:1.3.1
                    + androidx.activity:activity:1.4.0
                    
                    If this is intentional, re-baseline using ./gradlew :sample:app:manifestGuardBaseline
                    Or use ./gradlew manifestGuardBaseline to re-baseline dependencies in entire project.
                    
                    """.trimIndent()

                assertThat(actual)
                    .isEqualTo(expected)
            }
            else -> {
                fail("Wasn't expecting $result")
            }
        }
    }
}
