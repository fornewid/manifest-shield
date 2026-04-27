package io.github.fornewid.gradle.plugins.manifestshield

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.manifestshield.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.manifestshield.fixture.Builder.build
import io.github.fornewid.gradle.plugins.manifestshield.fixture.Builder.buildAndFail
import org.gradle.api.JavaVersion
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cross-version smoke tests against AGP 9.x to guard against regressions
 * introduced by AGP API or manifest-merger changes. The companion test class
 * [ManifestShieldPluginTest] still exercises full coverage on the minimum
 * supported AGP (8.5.0).
 */
internal class ManifestShieldPluginAgp9Test {

    @BeforeEach
    fun requireJava17() {
        // AGP 9.x and Gradle 9.3.1 require JDK 17+; skip on older JVMs to avoid
        // a confusing TestKit failure when run locally on JDK 11.
        assumeTrue(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17))
    }

    private fun newProject(
        pluginConfig: String = AndroidProject.DEFAULT_PLUGIN_CONFIG,
    ) = AndroidProject(
        pluginConfig = pluginConfig,
        agpVersion = AGP_VERSION,
        gradleVersion = GRADLE_VERSION,
    )

    @Test
    fun `baseline task creates merged baseline file on AGP 9`() {
        newProject().use { project ->
            val result = build(project, ":app:manifestShieldBaselineRelease")

            assertThat(result.output).contains("Manifest Shield baseline created")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission:")
            assertThat(baseline).contains("android.permission.INTERNET")
            assertThat(baseline).contains("activity:")
            assertThat(baseline).contains("MainActivity")
            assertThat(baseline).contains("(exported)")
        }
    }

    @Test
    fun `guard task fails when permission is added on AGP 9`() {
        newProject().use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.CAMERA" />
                    <application>
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent()
            )

            val result = buildAndFail(project, ":app:manifestShieldRelease")
            assertThat(result.output).contains("android.permission.CAMERA")
        }
    }

    @Test
    fun `sources baseline parses AGP 9 blame log`() {
        val pluginConfig = """
            manifestShield {
                configuration("release") {
                    sources = true
                }
            }
        """.trimIndent()

        newProject(pluginConfig = pluginConfig).use { project ->
            val result = build(project, ":app:manifestShieldSourcesBaselineRelease")
            assertThat(result.output).contains("Manifest Shield baseline created")

            val sources = project.readBaselineFile("manifestShield/releaseAndroidManifest.sources.txt")
            assertThat(sources).isNotNull()
            assertThat(sources).contains("[:app]")
        }
    }

    @Test
    fun `configuration cache is stored on AGP 9`() {
        newProject().use { project ->
            val result = build(
                project,
                ":app:manifestShieldBaselineRelease",
                "--configuration-cache",
            )
            assertThat(result.output).contains("Manifest Shield baseline created")
            assertThat(result.output).contains("Configuration cache entry stored")
        }
    }

    private companion object {
        const val AGP_VERSION = "9.1.0"
        const val GRADLE_VERSION = "9.3.1"
    }
}
