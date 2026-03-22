package io.github.fornewid.gradle.plugins.manifestshield

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.manifestshield.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.manifestshield.fixture.Builder.build
import io.github.fornewid.gradle.plugins.manifestshield.fixture.Builder.buildAndFail
import org.junit.jupiter.api.Test

internal class ManifestShieldPluginTest {

    @Test
    fun `baseline task creates merged baseline file`() {
        AndroidProject().use { project ->
            val result = build(project, ":app:manifestShieldBaselineRelease")

            assertThat(result.output).contains("Manifest Shield baseline created")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
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
            build(project, ":app:manifestShieldBaselineRelease")

            val result = build(project, ":app:manifestShieldRelease")
            assertThat(result.output).doesNotContain("Manifest Changed")
        }
    }

    @Test
    fun `guard task fails when permission is added`() {
        AndroidProject().use { project ->
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
    fun `guard task fails when activity is added`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

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

            val result = buildAndFail(project, ":app:manifestShieldRelease")
            assertThat(result.output).contains("NewActivity")
        }
    }

    @Test
    fun `guard task only checks configured categories`() {
        val pluginConfig = """
            manifestShield {
                configuration("release") {
                    usesPermission = true
                    activity = false
                    service = false
                    receiver = false
                    provider = false
                    usesFeature = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission:")
            assertThat(baseline).doesNotContain("activity:")
        }
    }

    @Test
    fun `re-baseline updates baseline files`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestShieldBaselineRelease")
            val initial = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")

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
            build(project, ":app:manifestShieldBaselineRelease")
            val updated = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")

            assertThat(updated).isNotEqualTo(initial)
            assertThat(updated).contains("android.permission.CAMERA")

            build(project, ":app:manifestShieldRelease")
        }
    }

    @Test
    fun `baseline uses custom directory when baselineDir is set`() {
        val pluginConfig = """
            manifestShield {
                baselineDir = "custom-baselines"
                configuration("release") {
                    usesPermission = true
                    activity = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            assertThat(project.readBaselineFile("custom-baselines/releaseAndroidManifest.txt")).isNotNull()
            assertThat(project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")).isNull()
        }
    }

    @Test
    fun `baseline excludes uses-sdk by default`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("uses-sdk:")
        }
    }

    @Test
    fun `baseline includes uses-sdk when enabled`() {
        val pluginConfig = """
            manifestShield {
                configuration("release") {
                    usesSdk = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-sdk:")
            assertThat(baseline).contains("minSdkVersion=")
        }
    }

    @Test
    fun `baseline excludes intent-filters by default`() {
        AndroidProject().use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("activity:")
            assertThat(baseline).doesNotContain("intent-filter:")
        }
    }

    @Test
    fun `baseline includes intent-filters when intentFilter is enabled`() {
        val pluginConfig = """
            manifestShield {
                configuration("release") {
                    intentFilter = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("intent-filter:")
            assertThat(baseline).contains("action: android.intent.action.MAIN")
            assertThat(baseline).contains("category: android.intent.category.LAUNCHER")
        }
    }

    @Test
    fun `baseline includes queries by default`() {
        AndroidProject().use { project ->
            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <queries>
                        <package android:name="com.example.foo" />
                    </queries>
                    <application>
                        <activity android:name=".MainActivity" android:exported="true" />
                    </application>
                </manifest>
                """.trimIndent()
            )

            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("queries:")
            assertThat(baseline).contains("  package: com.example.foo")
        }
    }

    @Test
    fun `baseline excludes queries when disabled`() {
        val pluginConfig = """
            manifestShield {
                configuration("release") {
                    queries = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <queries>
                        <package android:name="com.example.foo" />
                    </queries>
                    <application>
                        <activity android:name=".MainActivity" android:exported="true" />
                    </application>
                </manifest>
                """.trimIndent()
            )

            build(project, ":app:manifestShieldBaselineRelease")

            val baseline = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("queries:")
        }
    }

    @Test
    fun `sources baseline matches txt baseline content`() {
        val pluginConfig = """
            manifestShield {
                configuration("release") {
                    usesSdk = true
                    usesPermission = true
                    activity = true
                    sources = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:manifestShieldBaselineRelease")

            val txt = project.readBaselineFile("manifestShield/releaseAndroidManifest.txt")
            val sources = project.readBaselineFile("manifestShield/releaseAndroidManifest.sources.txt")
            assertThat(txt).isNotNull()
            assertThat(sources).isNotNull()

            // Both should contain the same categories
            assertThat(txt).contains("uses-sdk:")
            assertThat(sources).contains("uses-sdk:")
            assertThat(txt).contains("uses-permission:")
            assertThat(sources).contains("uses-permission:")
            assertThat(txt).contains("activity:")
            assertThat(sources).contains("activity:")
            // intent-filter is excluded by default (intentFilter = false)
            assertThat(txt).doesNotContain("intent-filter:")
            assertThat(sources).doesNotContain("intent-filter:")
        }
    }

    @Test
    fun `tasks report configuration cache incompatibility gracefully`() {
        AndroidProject().use { project ->
            val result = build(
                project,
                ":app:manifestShieldBaselineRelease",
                "--configuration-cache",
            )
            assertThat(result.output).contains("Manifest Shield baseline created")
        }
    }
}
