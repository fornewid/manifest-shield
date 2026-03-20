package io.github.fornewid.gradle.plugins.manifestguard

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.manifestguard.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.manifestguard.fixture.Builder.build
import io.github.fornewid.gradle.plugins.manifestguard.fixture.Builder.buildAndFail
import org.junit.jupiter.api.Test

internal class ManifestGuardPluginTest {

    @Test
    fun `baseline task creates permission and activity files`() {
        AndroidProject().use { project ->
            val result = build(project, ":app:manifestGuardBaselineRelease")

            assertThat(result.output).contains("Manifest Guard baseline created")

            val permissions = project.readBaselineFile("manifest-guard/release/permissions.txt")
            assertThat(permissions).isNotNull()
            assertThat(permissions).contains("android.permission.INTERNET")

            val activities = project.readBaselineFile("manifest-guard/release/activities.txt")
            assertThat(activities).isNotNull()
            assertThat(activities).contains("MainActivity")
            assertThat(activities).contains("(exported)")
        }
    }

    @Test
    fun `guard task passes when manifest has not changed`() {
        AndroidProject().use { project ->
            // Create baseline
            build(project, ":app:manifestGuardBaselineRelease")

            // Run guard - should pass
            val result = build(project, ":app:manifestGuardRelease")
            assertThat(result.output).doesNotContain("Manifest Changed")
        }
    }

    @Test
    fun `guard task fails when permission is added`() {
        AndroidProject().use { project ->
            // Create baseline
            build(project, ":app:manifestGuardBaselineRelease")

            // Add a new permission
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

            // Run guard - should fail
            val result = buildAndFail(project, ":app:manifestGuardRelease")
            assertThat(result.output).contains("android.permission.CAMERA")
        }
    }

    @Test
    fun `guard task fails when activity is added`() {
        AndroidProject().use { project ->
            // Create baseline
            build(project, ":app:manifestGuardBaselineRelease")

            // Add a new activity
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
            // Create baseline
            build(project, ":app:manifestGuardBaselineRelease")

            // Verify only permissions.txt is created
            assertThat(project.readBaselineFile("manifest-guard/release/permissions.txt")).isNotNull()
            assertThat(project.readBaselineFile("manifest-guard/release/activities.txt")).isNull()
            assertThat(project.readBaselineFile("manifest-guard/release/services.txt")).isNull()
        }
    }

    @Test
    fun `re-baseline updates baseline files`() {
        AndroidProject().use { project ->
            // Initial baseline
            build(project, ":app:manifestGuardBaselineRelease")
            val initialPermissions = project.readBaselineFile("manifest-guard/release/permissions.txt")

            // Add a permission and re-baseline
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
            val updatedPermissions = project.readBaselineFile("manifest-guard/release/permissions.txt")

            assertThat(updatedPermissions).isNotEqualTo(initialPermissions)
            assertThat(updatedPermissions).contains("android.permission.CAMERA")

            // Guard should now pass
            build(project, ":app:manifestGuardRelease")
        }
    }

    @Test
    fun `tasks report configuration cache incompatibility gracefully`() {
        AndroidProject().use { project ->
            // With --configuration-cache, the build should still succeed
            // because tasks declare notCompatibleWithConfigurationCache.
            // Gradle will skip caching but not fail.
            val result = build(
                project,
                ":app:manifestGuardBaselineRelease",
                "--configuration-cache",
            )
            assertThat(result.output).contains("Manifest Guard baseline created")
        }
    }
}
