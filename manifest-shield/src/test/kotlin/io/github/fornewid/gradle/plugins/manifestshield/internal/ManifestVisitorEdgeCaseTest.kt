package io.github.fornewid.gradle.plugins.manifestshield.internal

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ManifestVisitorEdgeCaseTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parse handles manifest without application element`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).hasSize(1)
        assertThat(result.activity).isEmpty()
        assertThat(result.service).isEmpty()
        assertThat(result.receiver).isEmpty()
        assertThat(result.provider).isEmpty()
    }

    @Test
    fun `parse handles minimal manifest`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).isEmpty()
        assertThat(result.activity).isEmpty()
        assertThat(result.service).isEmpty()
        assertThat(result.receiver).isEmpty()
        assertThat(result.provider).isEmpty()
        assertThat(result.usesFeature).isEmpty()
    }

    @Test
    fun `parse handles empty application element`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <application />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.activity).isEmpty()
        assertThat(result.service).isEmpty()
    }

    @Test
    fun `parse deduplicates identical permissions`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).hasSize(1)
    }

    @Test
    fun `parse ignores elements without android name attribute`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-permission />
                    <uses-feature />
                    <application>
                        <activity />
                    </application>
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).isEmpty()
        assertThat(result.usesFeature).isEmpty()
        assertThat(result.activity).isEmpty()
    }

    @Test
    fun `parse handles uses-feature without required attribute as required by default`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-feature android:name="android.hardware.camera" />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesFeature).hasSize(1)
        assertThat(result.usesFeature[0].required).isTrue()
        assertThat(result.usesFeature[0].toBaselineString()).isEqualTo("android.hardware.camera (required)")
    }

    @Test
    fun `parse synthesizes blame keys for queries-level intents matching AGP format`() {
        // Reproduces the exact composite-key shape AGP records in
        // manifest-merger-<variant>-report.txt for <queries><intent> children.
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <queries>
                        <intent>
                            <action android:name="android.intent.action.SEND" />
                            <data android:mimeType="image/*" />
                        </intent>
                        <intent>
                            <action android:name="android.intent.action.VIEW" />
                            <data android:scheme="https" />
                        </intent>
                        <intent>
                            <action android:name="android.intent.action.PICK" />
                        </intent>
                    </queries>
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        val keys = result.queries!!.intents.map { it.blameKey }
        assertThat(keys).containsExactly(
            "intent#action:name:android.intent.action.SEND+data:mimeType:image/*",
            "intent#action:name:android.intent.action.VIEW+data:scheme:https",
            "intent#action:name:android.intent.action.PICK",
        ).inOrder()
    }

    @Test
    fun `parse picks the first non-empty data attribute for the intent blame key`() {
        // AGP records only the first non-empty data attribute in the composite key,
        // following the order scheme → host → port → path → pathPrefix → pathPattern → mimeType.
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <queries>
                        <intent>
                            <action android:name="android.intent.action.VIEW" />
                            <data android:scheme="https" android:host="example.com" />
                        </intent>
                    </queries>
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.queries!!.intents.single().blameKey)
            .isEqualTo("intent#action:name:android.intent.action.VIEW+data:scheme:https")
    }
}
