package io.github.fornewid.gradle.plugins.manifestguard

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.manifestguard.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.manifestguard.fixture.Builder.build
import io.github.fornewid.gradle.plugins.manifestguard.fixture.Builder.buildAndFail
import org.junit.jupiter.api.Test

internal class ManifestGuardPluginTest {

    @Test
    fun `baseline task creates merged baseline file`() {
        AndroidProject().use { project ->
            val result = build(project, ":app:manifestGuardBaselineRelease")

            assertThat(result.output).contains("Manifest Guard baseline created")

            val baseline = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission:")
            assertThat(baseline).contains("android.permission.INTERNET")
            assertThat(baseline).contains("activity:")
            assertThat(baseline).contains("MainActivity")
            assertThat(baseline).contains("(exported)")

            // Empty categories should not appear
            assertThat(baseline).doesNotContain("uses-feature:")
            assertThat(baseline).doesNotContain("service:")
            assertThat(baseline).doesNotContain("receiver:")
            assertThat(baseline).doesNotContain("provider:")
        }
    }

    @Test
    fun `guard task passes when manifest has not changed`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            val result = build(project, ":app:manifestGuardRelease")
            assertThat(result.output).doesNotContain("Manifest Changed")
        }
    }

    @Test
    fun `guard task fails when permission is added`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

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

            val result = buildAndFail(project, ":app:manifestGuardRelease")
            assertThat(result.output).contains("android.permission.CAMERA")
        }
    }

    @Test
    fun `guard task fails when activity is added`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <application>
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                        <activity android:name=".NewActivity" />
                    </application>
                </manifest>
                """.trimIndent()
            )

            val result = buildAndFail(project, ":app:manifestGuardRelease")
            assertThat(result.output).contains("NewActivity")
        }
    }

    @Test
    fun `guard task only checks configured categories`() {
        val pluginConfig = """
            manifestGuard {
                configuration("release") {
                    permissions = true
                    activities = false
                    services = false
                    receivers = false
                    providers = false
                    features = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            val baseline = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission:")
            assertThat(baseline).doesNotContain("activity:")
        }
    }

    @Test
    fun `re-baseline updates baseline files`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestGuardBaselineRelease")
            val initial = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")

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
            build(project, ":app:manifestGuardBaselineRelease")
            val updated = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")

            assertThat(updated).isNotEqualTo(initial)
            assertThat(updated).contains("android.permission.CAMERA")

            build(project, ":app:manifestGuardRelease")
        }
    }

    @Test
    fun `baseline uses custom directory when baselineDir is set`() {
        val pluginConfig = """
            manifestGuard {
                baselineDir = "custom-baselines"
                configuration("release") {
                    permissions = true
                    activities = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            assertThat(project.readBaselineFile("custom-baselines/releaseAndroidManifest.txt")).isNotNull()
            assertThat(project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")).isNull()
        }
    }

    @Test
    fun `baseline excludes sdk when sdk is disabled`() {
        val pluginConfig = """
            manifestGuard {
                configuration("release") {
                    sdk = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            val baseline = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("uses-sdk:")
            assertThat(baseline).contains("uses-permission:")
        }
    }

    @Test
    fun `baseline excludes intent-filters when intentFilters is disabled`() {
        val pluginConfig = """
            manifestGuard {
                configuration("release") {
                    intentFilters = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            val baseline = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("activity:")
            assertThat(baseline).doesNotContain("intent-filter:")
        }
    }

    @Test
    fun `baseline includes intent-filters by default`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            val baseline = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("intent-filter:")
            assertThat(baseline).contains("action: android.intent.action.MAIN")
            assertThat(baseline).contains("category: android.intent.category.LAUNCHER")
        }
    }

    @Test
    fun `sources baseline matches txt baseline content`() {
        val pluginConfig = """
            manifestGuard {
                configuration("release") {
                    permissions = true
                    activities = true
                    sources = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestGuardBaselineRelease")

            val txt = project.readBaselineFile("manifestGuard/releaseAndroidManifest.txt")
            val sources = project.readBaselineFile("manifestGuard/releaseAndroidManifest.sources.txt")
            assertThat(txt).isNotNull()
            assertThat(sources).isNotNull()

            // Both should contain the same categories
            assertThat(txt).contains("uses-sdk:")
            assertThat(sources).contains("uses-sdk:")
            assertThat(txt).contains("uses-permission:")
            assertThat(sources).contains("uses-permission:")
            assertThat(txt).contains("activity:")
            assertThat(sources).contains("activity:")
            assertThat(txt).contains("intent-filter:")
            assertThat(sources).contains("intent-filter:")
        }
    }

    @Test
    fun `tasks report configuration cache incompatibility gracefully`() {
        AndroidProject().use { project ->
            val result = build(
                project,
                ":app:manifestGuardBaselineRelease",
                "--configuration-cache",
            )
            assertThat(result.output).contains("Manifest Guard baseline created")
        }
    }
}
