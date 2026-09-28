package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestListDiffTest {

    @Test
    fun `no diff when contents are identical`() {
        val result = ManifestListDiff.performDiff(
            projectPath = ":app",
            configurationName = "release",
            category = "permissions",
            expectedContent = "android.permission.INTERNET\nandroid.permission.CAMERA\n",
            actualContent = "android.permission.INTERNET\nandroid.permission.CAMERA\n",
        )
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.NoDiff::class.java)
    }

    @Test
    fun `has diff when entry added`() {
        val result = ManifestListDiff.performDiff(
            projectPath = ":app",
            configurationName = "release",
            category = "permissions",
            expectedContent = "android.permission.INTERNET\n",
            actualContent = "android.permission.INTERNET\nandroid.permission.CAMERA\n",
        )
        assertThat(diffText(result)).isEqualTo("+ android.permission.CAMERA\n")
    }

    @Test
    fun `has diff when entry removed`() {
        val result = ManifestListDiff.performDiff(
            projectPath = ":app",
            configurationName = "release",
            category = "activities",
            expectedContent = "com.example.MainActivity (exported)\ncom.example.DetailActivity\n",
            actualContent = "com.example.MainActivity (exported)\n",
        )
        assertThat(diffText(result)).isEqualTo("- com.example.DetailActivity\n")
    }

    @Test
    fun `has diff when component moves to another section`() {
        val expected = """
            service:
              com.example.SyncService (exported)

            receiver:
              com.example.PushReceiver (exported)

        """.trimIndent()
        val actual = """
            service:
              com.example.PushReceiver (exported)

            receiver:
              com.example.SyncService (exported)

        """.trimIndent()

        assertThat(diff(expected, actual)).isEqualTo(
            """
              service:
            +   com.example.PushReceiver (exported)
            -   com.example.SyncService (exported)
              receiver:
            -   com.example.PushReceiver (exported)
            +   com.example.SyncService (exported)

            """.trimIndent()
        )
    }

    @Test
    fun `has diff when permission moves to another component, replacing whole entries`() {
        val expected = """
            service:
              com.example.SyncService (exported)
                permission: android.permission.BIND_JOB_SERVICE
              com.example.UploadService (exported)

        """.trimIndent()
        val actual = """
            service:
              com.example.SyncService (exported)
              com.example.UploadService (exported)
                permission: android.permission.BIND_JOB_SERVICE

        """.trimIndent()

        assertThat(diff(expected, actual)).isEqualTo(
            """
              service:
            -   com.example.SyncService (exported)
            -     permission: android.permission.BIND_JOB_SERVICE
            +   com.example.SyncService (exported)
            -   com.example.UploadService (exported)
            +   com.example.UploadService (exported)
            +     permission: android.permission.BIND_JOB_SERVICE

            """.trimIndent()
        )
    }

    @Test
    fun `has diff when added lines already exist under another component`() {
        val expected = """
            activity:
              com.example.A (exported)
                intent-filter:
                  action: android.intent.action.VIEW
                  category: android.intent.category.BROWSABLE
              com.example.B (exported)

        """.trimIndent()
        val actual = """
            activity:
              com.example.A (exported)
                intent-filter:
                  action: android.intent.action.VIEW
                  category: android.intent.category.BROWSABLE
              com.example.B (exported)
                intent-filter:
                  action: android.intent.action.VIEW
                  category: android.intent.category.BROWSABLE

        """.trimIndent()

        assertThat(diff(expected, actual)).isEqualTo(
            """
              activity:
            -   com.example.B (exported)
            +   com.example.B (exported)
            +     intent-filter:
            +       action: android.intent.action.VIEW
            +       category: android.intent.category.BROWSABLE

            """.trimIndent()
        )
    }

    @Test
    fun `no diff when only order changes`() {
        val expected = """
            uses-permission:
              android.permission.CAMERA
              android.permission.INTERNET

            activity:
              com.example.A (exported)
                intent-filter:
                  action: android.intent.action.VIEW
                  data: https://a.example.com
                intent-filter:
                  action: android.intent.action.VIEW
                  data: https://b.example.com

            queries:
              intent:
                action: android.intent.action.SEND
              intent:
                action: android.intent.action.VIEW

        """.trimIndent()
        val actual = """
            activity:
              com.example.A (exported)
                intent-filter:
                  action: android.intent.action.VIEW
                  data: https://b.example.com
                intent-filter:
                  action: android.intent.action.VIEW
                  data: https://a.example.com

            queries:
              intent:
                action: android.intent.action.VIEW
              intent:
                action: android.intent.action.SEND

            uses-permission:
              android.permission.INTERNET
              android.permission.CAMERA

        """.trimIndent()

        val result = ManifestListDiff.performDiff(":app", "release", "releaseAndroidManifest", expected, actual)
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.NoDiff::class.java)
    }

    @Test
    fun `no diff when only line endings or blank lines differ`() {
        val expected = "uses-permission:\n  android.permission.CAMERA\n\nservice:\n  com.example.S (exported)\n"
        val actual = "uses-permission:\r\n  android.permission.CAMERA\r\nservice:\r\n  com.example.S (exported)"

        val result = ManifestListDiff.performDiff(":app", "release", "releaseAndroidManifest", expected, actual)
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.NoDiff::class.java)
    }

    @Test
    fun `prints entries under their section header`() {
        val expected = """
            uses-permission:
              android.permission.INTERNET

        """.trimIndent()
        val actual = """
            uses-permission:
              android.permission.CAMERA
              android.permission.INTERNET

            service:
              com.example.fakesdk.SyncService (exported)

            receiver:
              com.example.fakesdk.PushReceiver (exported)

            meta-data:
              com.example.fakesdk.AUTO_INIT (true)

        """.trimIndent()

        assertThat(diff(expected, actual)).isEqualTo(
            """
              uses-permission:
            +   android.permission.CAMERA
            + service:
            +   com.example.fakesdk.SyncService (exported)
            + receiver:
            +   com.example.fakesdk.PushReceiver (exported)
            + meta-data:
            +   com.example.fakesdk.AUTO_INIT (true)

            """.trimIndent()
        )
    }

    @Test
    fun `keeps removed section at its baseline position`() {
        val expected = """
            uses-permission:
              android.permission.INTERNET

            service:
              com.example.S (exported)

            receiver:
              com.example.R (exported)

        """.trimIndent()
        val actual = """
            uses-permission:
              android.permission.INTERNET

            receiver:
              com.example.R (exported)

        """.trimIndent()

        assertThat(diff(expected, actual)).isEqualTo(
            """
            - service:
            -   com.example.S (exported)

            """.trimIndent()
        )
    }

    @Test
    fun `prints source group and section headers for sources baseline`() {
        val expected = """
            [:app]
            activity:
              com.example.MainActivity (exported)

            [com.example:sdk:1.0]
            service:
              com.example.sdk.SyncService (exported)

        """.trimIndent()
        val actual = """
            [:app]
            activity:
              com.example.MainActivity (exported)

            [com.example:sdk:1.0]
            receiver:
              com.example.sdk.SyncService (exported)

        """.trimIndent()

        assertThat(diff(expected, actual)).isEqualTo(
            """
              [com.example:sdk:1.0]
            - service:
            -   com.example.sdk.SyncService (exported)
            + receiver:
            +   com.example.sdk.SyncService (exported)

            """.trimIndent()
        )
    }

    private fun diff(expected: String, actual: String): String =
        diffText(ManifestListDiff.performDiff(":app", "release", "releaseAndroidManifest", expected, actual))

    private fun diffText(result: ManifestListDiffResult.DiffPerformed): String {
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.HasDiff::class.java)
        return (result as ManifestListDiffResult.DiffPerformed.HasDiff).removedAndAddedLines.diffTextWithPlusAndMinus
    }
}
