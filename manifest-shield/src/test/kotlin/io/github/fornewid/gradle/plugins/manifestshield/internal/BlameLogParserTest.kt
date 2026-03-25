package io.github.fornewid.gradle.plugins.manifestshield.internal

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

internal class BlameLogParserTest {

    private val blameLogFile = File(javaClass.classLoader.getResource("test-blame-log.txt")!!.toURI())
    private val projectDir = File("/path/to")

    @Test
    fun `parse extracts blame entries`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        assertThat(entries).isNotEmpty()
    }

    @Test
    fun `parse identifies app module sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val internetPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetPermission.source).isEqualTo(":app")
    }

    @Test
    fun `parse captures MERGED sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val internetEntries = entries.filter {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetEntries).hasSize(2)
        assertThat(internetEntries.map { it.source }).containsExactly(":app", ":module1")
    }

    @Test
    fun `parse identifies library sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val networkPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.ACCESS_NETWORK_STATE"
        }
        assertThat(networkPermission.source).isEqualTo("com.google.firebase:firebase-core:21.0.0")
    }

    @Test
    fun `parse extracts activity sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val firebaseActivity = entries.first {
            it.elementType == "activity" && it.elementName == "com.google.firebase.FirebaseActivity"
        }
        assertThat(firebaseActivity.source).isEqualTo("com.google.firebase:firebase-core:21.0.0")

        val mainActivity = entries.first {
            it.elementType == "activity" && it.elementName == "com.example.app.MainActivity"
        }
        assertThat(mainActivity.source).isEqualTo(":app")
    }

    @Test
    fun `parse extracts service sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val messagingService = entries.first {
            it.elementType == "service" && it.elementName == "com.google.firebase.messaging.FirebaseMessagingService"
        }
        assertThat(messagingService.source).isEqualTo("com.google.firebase:firebase-messaging:23.0.0")
    }

    @Test
    fun `parse extracts feature sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val locationFeature = entries.first {
            it.elementType == "uses-feature" && it.elementName == "android.hardware.location"
        }
        assertThat(locationFeature.source).isEqualTo("com.google.android.gms:play-services-location:21.0.0")
    }

    @Test
    fun `buildSourceMap creates element-to-sources mapping with multiple sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)
        val sourceMap = BlameLogParser.buildSourceMap(entries)

        // INTERNET has both ADDED from :app and MERGED from :module1
        assertThat(sourceMap["uses-permission#android.permission.INTERNET"])
            .containsExactly(":app", ":module1")

        assertThat(sourceMap["uses-permission#android.permission.ACCESS_NETWORK_STATE"])
            .containsExactly("com.google.firebase:firebase-core:21.0.0")

        assertThat(sourceMap["activity#com.google.firebase.FirebaseActivity"])
            .containsExactly("com.google.firebase:firebase-core:21.0.0")
    }

    @Test
    fun `parse returns empty list for non-existent file`() {
        val entries = BlameLogParser.parse(File("/non/existent/file.txt"))
        assertThat(entries).isEmpty()
    }

    @Test
    fun `parse handles real AGP 8 blame log format`() {
        val agp8BlameLog = File(javaClass.classLoader.getResource("test-blame-log-agp8.txt")!!.toURI())
        val agp8ProjectDir = File("/Users/dev/MyApp")
        val entries = BlameLogParser.parse(agp8BlameLog, agp8ProjectDir)

        // ADDED with range format (2:1-23:12)
        val internet = entries.filter {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internet).hasSize(2)
        assertThat(internet.map { it.source }).containsExactly(":app", ":module1")

        // Library source with gradle cache path
        val firebaseService = entries.first {
            it.elementType == "service" && it.elementName == "com.google.firebase.components.ComponentDiscoveryService"
        }
        assertThat(firebaseService.source).isEqualTo("com.google.firebase:firebase-common:20.0.0")

        // Elements without a name (like uses-sdk) are skipped by the parser
        val sdk = entries.filter { it.elementType == "uses-sdk" }
        assertThat(sdk).isEmpty()

        // Both ADDED and IMPLIED actions for MainActivity should be parsed
        val mainActivityEntries = entries.filter {
            it.elementType == "activity" && it.elementName == "com.example.app.MainActivity"
        }
        assertThat(mainActivityEntries).hasSize(2)
        assertThat(mainActivityEntries.map { it.source }).containsExactly(":app", ":app")
    }

    @Test
    fun `parse without projectDir uses raw file path as source`() {
        val entries = BlameLogParser.parse(blameLogFile)

        val internetPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetPermission.source).startsWith("/path/to/app/src/main/")
    }

    @Test
    fun `parse handles file paths with spaces`() {
        val spacesBlameLog = File(javaClass.classLoader.getResource("test-blame-log-spaces.txt")!!.toURI())
        val spacesProjectDir = File("/Users/John Doe/My Projects")
        val entries = BlameLogParser.parse(spacesBlameLog, spacesProjectDir)

        val internetPermission = entries.filter {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetPermission).hasSize(2)
        assertThat(internetPermission.map { it.source }).containsExactly(":app", ":module1")

        val cameraPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.CAMERA"
        }
        assertThat(cameraPermission.source).isEqualTo("com.google.firebase:firebase-core:21.0.0")

        val mainActivity = entries.first {
            it.elementType == "activity" && it.elementName == "com.example.app.MainActivity"
        }
        assertThat(mainActivity.source).isEqualTo(":app")
    }
}
