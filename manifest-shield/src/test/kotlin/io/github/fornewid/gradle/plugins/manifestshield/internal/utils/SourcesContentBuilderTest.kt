package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.manifestshield.internal.EnabledCategories
import io.github.fornewid.gradle.plugins.manifestshield.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.manifestshield.models.ComponentType
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestPermission
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestProfileable
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestQuery
import io.github.fornewid.gradle.plugins.manifestshield.models.ManifestSdk
import io.github.fornewid.gradle.plugins.manifestshield.models.QueryIntent
import org.junit.jupiter.api.Test

internal class SourcesContentBuilderTest {

    @Test
    fun `build with empty source map appends unresolved marker`() {
        val entries = listOf(
            ManifestPermission("android.permission.INTERNET"),
            ManifestPermission("android.permission.CAMERA"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "uses-permission",
            sourceMap = emptyMap(),
        )
        assertThat(result).contains("android.permission.INTERNET -- <unresolved>")
        assertThat(result).contains("android.permission.CAMERA -- <unresolved>")
    }

    @Test
    fun `build groups entries by source with app first`() {
        val entries = listOf(
            ManifestPermission("android.permission.CAMERA"),
            ManifestPermission("android.permission.INTERNET"),
            ManifestPermission("android.permission.WAKE_LOCK"),
        )
        val sourceMap = mapOf(
            "uses-permission#android.permission.INTERNET" to listOf("app"),
            "uses-permission#android.permission.CAMERA" to listOf("app"),
            "uses-permission#android.permission.WAKE_LOCK" to listOf("com.google.firebase:firebase-messaging:23.0.0"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "uses-permission",
            sourceMap = sourceMap,
        )
        val lines = result.lines().filter { it.isNotBlank() }
        assertThat(lines[0]).isEqualTo("app:")
        assertThat(lines[1]).isEqualTo("  android.permission.CAMERA")
        assertThat(lines[2]).isEqualTo("  android.permission.INTERNET")
        assertThat(lines[3]).isEqualTo("com.google.firebase:firebase-messaging:23.0.0:")
        assertThat(lines[4]).isEqualTo("  android.permission.WAKE_LOCK")
    }

    @Test
    fun `build preserves exported annotation in components`() {
        val entries = listOf(
            ManifestComponent("com.example.MainActivity", ComponentType.ACTIVITY, exported = true),
            ManifestComponent("com.example.DetailActivity", ComponentType.ACTIVITY, exported = false),
        )
        val sourceMap = mapOf(
            "activity#com.example.MainActivity" to listOf("app"),
            "activity#com.example.DetailActivity" to listOf("app"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "activity",
            sourceMap = sourceMap,
        )
        assertThat(result).contains("com.example.DetailActivity")
        assertThat(result).contains("com.example.MainActivity (exported)")
    }

    @Test
    fun `build with multiple libraries sorts libraries alphabetically`() {
        val entries = listOf(
            ManifestComponent("com.z.ZActivity", ComponentType.ACTIVITY, exported = null),
            ManifestComponent("com.a.AActivity", ComponentType.ACTIVITY, exported = null),
        )
        val sourceMap = mapOf(
            "activity#com.z.ZActivity" to listOf("com.z:lib:1.0"),
            "activity#com.a.AActivity" to listOf("com.a:lib:1.0"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "activity",
            sourceMap = sourceMap,
        )
        val sourceLines = result.lines().filter { !it.startsWith("  ") && it.isNotBlank() }
        assertThat(sourceLines).containsExactly("com.a:lib:1.0:", "com.z:lib:1.0:").inOrder()
    }

    @Test
    fun `build with empty entries returns empty string`() {
        val result = SourcesContentBuilder.build(
            entries = emptyList(),
            elementType = "uses-permission",
            sourceMap = emptyMap(),
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `buildMergedWithSdk attributes singleton elements via sourceMap`() {
        val manifest = emptyManifest().copy(
            usesSdk = ManifestSdk(minSdkVersion = "23", targetSdkVersion = "36"),
            profileable = ManifestProfileable(shell = true, enabled = null),
        )
        val sourceMap = mapOf(
            "uses-sdk" to listOf(":app"),
            "profileable" to listOf("com.example:profiler:1.0.0"),
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            projectPath = ":app",
            flags = enableSingletons(),
        )

        assertThat(result).contains("[:app]")
        assertThat(result).contains("uses-sdk:")
        assertThat(result).contains("  minSdkVersion=23")
        assertThat(result).contains("  targetSdkVersion=36")
        assertThat(result).contains("[com.example:profiler:1.0.0]")
        // profileable is attributed to the library, NOT to :app
        val appSection = result.substringAfter("[:app]").substringBefore("[com.")
        assertThat(appSection).doesNotContain("profileable:")
    }

    @Test
    fun `buildMergedWithSdk attributes queries packages per child`() {
        // App declares <queries><package="com.example.helper"/></queries>.
        // Library injects <queries><package="com.google.android.gms"/></queries>.
        // Merged manifest has both packages, but each has its own blame entry.
        val manifest = emptyManifest().copy(
            queries = ManifestQuery(
                packages = listOf("com.example.helper", "com.google.android.gms"),
                intents = emptyList(),
                providers = emptyList(),
            ),
        )
        val sourceMap = mapOf(
            "queries" to listOf(":app", "com.google.android.gms:play-services-base:18.5.0"),
            "package#com.example.helper" to listOf(":app"),
            "package#com.google.android.gms" to listOf("com.google.android.gms:play-services-base:18.5.0"),
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            projectPath = ":app",
            flags = enableSingletons(),
        )

        val appSection = result.substringAfter("[:app]").substringBefore("[com.")
        val librarySection = result.substringAfter("[com.google.android.gms:play-services-base:18.5.0]")

        // helper appears under :app only
        assertThat(appSection).contains("package: com.example.helper")
        assertThat(librarySection).doesNotContain("package: com.example.helper")
        // gms appears under the library only
        assertThat(librarySection).contains("package: com.google.android.gms")
        assertThat(appSection).doesNotContain("package: com.google.android.gms")
    }

    @Test
    fun `buildMergedWithSdk falls back to unresolved when query package source is missing`() {
        val manifest = emptyManifest().copy(
            queries = ManifestQuery(
                packages = listOf("com.unknown"),
                intents = emptyList(),
                providers = emptyList(),
            ),
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = emptyMap(),
            projectPath = ":app",
            flags = enableSingletons(),
        )

        assertThat(result).contains("[<unresolved>]")
        val unresolvedSection = result.substringAfter("[<unresolved>]")
        assertThat(unresolvedSection).contains("queries:")
        assertThat(unresolvedSection).contains("package: com.unknown")
    }

    @Test
    fun `buildMergedWithSdk attributes queries intents per child via composite blame key`() {
        // App declares two intents, library injects one — each must land in its own
        // source group, not duplicated like the old container-based fallback did.
        val sendKey = "intent#action:name:android.intent.action.SEND+data:mimeType:image/*"
        val viewKey = "intent#action:name:android.intent.action.VIEW+data:scheme:https"
        val pickKey = "intent#action:name:android.intent.action.PICK"

        val manifest = emptyManifest().copy(
            queries = ManifestQuery(
                packages = emptyList(),
                intents = listOf(
                    QueryIntent(actions = listOf("android.intent.action.SEND"), categories = emptyList(),
                        dataSpecs = listOf("image/*"), blameKey = sendKey),
                    QueryIntent(actions = listOf("android.intent.action.VIEW"), categories = emptyList(),
                        dataSpecs = listOf("https://"), blameKey = viewKey),
                    QueryIntent(actions = listOf("android.intent.action.PICK"), categories = emptyList(),
                        dataSpecs = emptyList(), blameKey = pickKey),
                ),
                providers = emptyList(),
            ),
        )
        val sourceMap = mapOf(
            sendKey to listOf(":app"),
            viewKey to listOf(":app"),
            pickKey to listOf(":sample:module1"),
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            projectPath = ":app",
            flags = enableSingletons(),
        )

        val appSection = result.substringAfter("[:app]").substringBefore("[:")
        val module1Section = result.substringAfter("[:sample:module1]")

        // SEND and VIEW belong to :app
        assertThat(appSection).contains("action: android.intent.action.SEND")
        assertThat(appSection).contains("action: android.intent.action.VIEW")
        assertThat(appSection).doesNotContain("action: android.intent.action.PICK")
        // PICK belongs to :sample:module1
        assertThat(module1Section).contains("action: android.intent.action.PICK")
        assertThat(module1Section).doesNotContain("action: android.intent.action.SEND")
        assertThat(module1Section).doesNotContain("action: android.intent.action.VIEW")
        assertThat(result).doesNotContain("[<unresolved>]")
    }

    @Test
    fun `buildMergedWithSdk routes intent to unresolved when blame key is missing`() {
        val unknownKey = "intent#action:name:com.example.UNKNOWN"
        val manifest = emptyManifest().copy(
            queries = ManifestQuery(
                packages = emptyList(),
                intents = listOf(
                    QueryIntent(actions = listOf("com.example.UNKNOWN"), categories = emptyList(),
                        dataSpecs = emptyList(), blameKey = unknownKey),
                ),
                providers = emptyList(),
            ),
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = emptyMap(),
            projectPath = ":app",
            flags = enableSingletons(),
        )

        assertThat(result).contains("[<unresolved>]")
        val unresolvedSection = result.substringAfter("[<unresolved>]")
        assertThat(unresolvedSection).contains("intent:")
        assertThat(unresolvedSection).contains("action: com.example.UNKNOWN")
    }

    @Test
    fun `buildMergedWithSdk places unresolved group last when other sources exist`() {
        val manifest = emptyManifest().copy(
            usesSdk = ManifestSdk(minSdkVersion = "23", targetSdkVersion = null),
            queries = ManifestQuery(
                packages = listOf("com.resolved", "com.missing"),
                intents = emptyList(),
                providers = emptyList(),
            ),
        )
        val sourceMap = mapOf(
            "uses-sdk" to listOf(":app"),
            "package#com.resolved" to listOf("com.example:lib:1.0.0"),
            // package#com.missing is intentionally missing → routed to <unresolved>
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            projectPath = ":app",
            flags = enableSingletons(),
        )

        // Local module (`:app`) appears first, external library next, <unresolved> last
        val appIdx = result.indexOf("[:app]")
        val libIdx = result.indexOf("[com.example:lib:1.0.0]")
        val unresolvedIdx = result.indexOf("[<unresolved>]")
        assertThat(appIdx).isAtLeast(0)
        assertThat(libIdx).isGreaterThan(appIdx)
        assertThat(unresolvedIdx).isGreaterThan(libIdx)
    }

    @Test
    fun `buildMergedWithSdk uses unresolved group when sourceMap misses singleton`() {
        val manifest = emptyManifest().copy(
            queries = ManifestQuery(
                packages = listOf("com.unknown"),
                intents = emptyList(),
                providers = emptyList(),
            ),
        )
        // sourceMap is empty — simulating a parser miss for the queries element

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = emptyMap(),
            projectPath = ":app",
            flags = enableSingletons(),
        )

        assertThat(result).contains("[<unresolved>]")
        // <unresolved> sorts last
        assertThat(result.indexOf("[<unresolved>]")).isAtLeast(0)
    }

    @Test
    fun `buildMergedWithSdk does not silently route singletons to projectPath`() {
        val manifest = emptyManifest().copy(
            queries = ManifestQuery(
                packages = listOf("com.from.library"),
                intents = emptyList(),
                providers = emptyList(),
            ),
        )
        val sourceMap = mapOf(
            "queries" to listOf("com.example:lib:1.0.0"),
            "package#com.from.library" to listOf("com.example:lib:1.0.0"),
        )

        val result = SourcesContentBuilder.buildMergedWithSdk(
            manifest = manifest,
            sourceMap = sourceMap,
            projectPath = ":app",
            flags = enableSingletons(),
        )

        // queries should appear ONLY under the library group, not under [:app]
        assertThat(result).doesNotContain("[:app]")
        assertThat(result).contains("[com.example:lib:1.0.0]")
        assertThat(result).contains("queries:")
    }

    private fun emptyManifest() = ManifestExtraction(
        usesSdk = null,
        usesPermission = emptyList(),
        usesPermissionSdk23 = emptyList(),
        permission = emptyList(),
        usesFeature = emptyList(),
        supportsScreens = null,
        compatibleScreens = emptyList(),
        usesConfiguration = null,
        supportsGlTextures = emptyList(),
        queries = null,
        activity = emptyList(),
        activityAlias = emptyList(),
        metaData = emptyList(),
        service = emptyList(),
        receiver = emptyList(),
        provider = emptyList(),
        usesLibraries = emptyList(),
        usesNativeLibraries = emptyList(),
        profileable = null,
        startupInitializers = emptyList(),
    )

    private fun enableSingletons() = EnabledCategories(
        usesSdk = true, usesFeature = false, usesPermission = false, usesPermissionSdk23 = false,
        permission = false, supportsScreens = true, compatibleScreens = true, usesConfiguration = true,
        supportsGlTexture = false, queries = true, activity = false, activityAlias = false,
        metaData = false, service = false, receiver = false, provider = false,
        usesLibrary = false, usesNativeLibrary = false, profileable = true, intentFilter = false,
        startup = false, exportedOnly = true, requiredOnly = true, unprotectedOnly = true,
    )
}
