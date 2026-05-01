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

        // Singleton elements (no android:name) are still attributed by element type alone.
        // uses-sdk in this fixture is `INJECTED from <app>/AndroidManifest.xml`.
        val sdk = entries.filter { it.elementType == "uses-sdk" }
        assertThat(sdk).hasSize(1)
        assertThat(sdk.single().elementName).isNull()
        assertThat(sdk.single().source).isEqualTo(":app")

        // Both ADDED and IMPLIED actions for MainActivity should be parsed
        val mainActivityEntries = entries.filter {
            it.elementType == "activity" && it.elementName == "com.example.app.MainActivity"
        }
        assertThat(mainActivityEntries).hasSize(2)
        assertThat(mainActivityEntries.map { it.source }).containsExactly(":app", ":app")
    }

    @Test
    fun `parse captures singleton elements by element type alone`() {
        val singletonsLog = File(javaClass.classLoader.getResource("test-blame-log-singletons.txt")!!.toURI())
        val rootDir = File("/Users/dev/MyApp")
        val entries = BlameLogParser.parse(singletonsLog, rootDir)

        // uses-sdk: AGP-injected from the app manifest, single source
        val sdk = entries.filter { it.elementType == "uses-sdk" }
        assertThat(sdk).hasSize(1)
        assertThat(sdk.single().elementName).isNull()
        assertThat(sdk.single().source).isEqualTo(":app")

        // queries: declared by app AND merged from a library — two sources
        val queries = entries.filter { it.elementType == "queries" }
        assertThat(queries).hasSize(2)
        assertThat(queries.map { it.source }).containsExactly(
            ":app",
            "com.google.android.gms:play-services-base:18.5.0",
        )

        // supports-screens: app-only
        val supportsScreens = entries.filter { it.elementType == "supports-screens" }
        assertThat(supportsScreens).hasSize(1)
        assertThat(supportsScreens.single().source).isEqualTo(":app")

        // compatible-screens: library-only
        val compatibleScreens = entries.filter { it.elementType == "compatible-screens" }
        assertThat(compatibleScreens).hasSize(1)
        assertThat(compatibleScreens.single().source).isEqualTo("com.example:legacy-screens:1.0.0")

        // uses-configuration / profileable: app-only
        assertThat(entries.filter { it.elementType == "uses-configuration" }.map { it.source })
            .containsExactly(":app")
        assertThat(entries.filter { it.elementType == "profileable" }.map { it.source })
            .containsExactly(":app")
    }

    @Test
    fun `buildSourceMap keys singleton elements by type alone`() {
        val singletonsLog = File(javaClass.classLoader.getResource("test-blame-log-singletons.txt")!!.toURI())
        val rootDir = File("/Users/dev/MyApp")
        val entries = BlameLogParser.parse(singletonsLog, rootDir)
        val sourceMap = BlameLogParser.buildSourceMap(entries)

        assertThat(sourceMap["uses-sdk"]).containsExactly(":app")
        assertThat(sourceMap["queries"])
            .containsExactly(":app", "com.google.android.gms:play-services-base:18.5.0")
        assertThat(sourceMap["compatible-screens"])
            .containsExactly("com.example:legacy-screens:1.0.0")

        // Name-keyed elements still use the "$type#$name" key
        assertThat(sourceMap["package#com.example.helper"]).containsExactly(":app")
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
