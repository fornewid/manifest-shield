package io.github.fornewid.gradle.plugins.manifestguard.internal

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.manifestguard.models.ComponentType
import org.junit.jupiter.api.Test
import java.io.File

internal class ManifestVisitorTest {

    private val manifestFile = File(javaClass.classLoader.getResource("test-manifest.xml")!!.toURI())

    @Test
    fun `parse extracts permissions`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.permissions.map { it.name }).containsExactly(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA",
            "android.permission.INTERNET",
        ).inOrder()
    }

    @Test
    fun `parse extracts features`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.features).hasSize(2)
        assertThat(result.features[0].name).isEqualTo("android.hardware.camera")
        assertThat(result.features[0].required).isTrue()
        assertThat(result.features[1].name).isEqualTo("android.hardware.location")
        assertThat(result.features[1].required).isFalse()
    }

    @Test
    fun `parse extracts features baseline strings`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.features.map { it.toBaselineString() }).containsExactly(
            "android.hardware.camera (required)",
            "android.hardware.location",
        ).inOrder()
    }

    @Test
    fun `parse extracts activities with exported flag`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.activities).hasSize(3)
        val mainActivity = result.activities.first { it.name == "com.example.app.MainActivity" }
        assertThat(mainActivity.exported).isTrue()
        assertThat(mainActivity.type).isEqualTo(ComponentType.ACTIVITY)

        val detailActivity = result.activities.first { it.name == "com.example.app.DetailActivity" }
        assertThat(detailActivity.exported).isFalse()

        val firebaseActivity = result.activities.first { it.name == "com.google.firebase.FirebaseActivity" }
        assertThat(firebaseActivity.exported).isNull()
    }

    @Test
    fun `parse extracts activity baseline strings with exported annotation`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.activities.map { it.toBaselineString() }).containsExactly(
            "com.example.app.DetailActivity",
            "com.example.app.MainActivity (exported)",
            "com.google.firebase.FirebaseActivity",
        ).inOrder()
    }

    @Test
    fun `parse extracts services`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.services).hasSize(2)
        assertThat(result.services.map { it.toBaselineString() }).containsExactly(
            "com.example.app.MyService",
            "com.google.firebase.messaging.FirebaseMessagingService (exported)",
        ).inOrder()
    }

    @Test
    fun `parse extracts receivers`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.receivers).hasSize(1)
        assertThat(result.receivers[0].name).isEqualTo("com.example.app.BootReceiver")
    }

    @Test
    fun `parse extracts providers`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.providers).hasSize(1)
        assertThat(result.providers[0].toBaselineString()).isEqualTo("com.example.app.MyContentProvider (exported, authorities=com.example.app.provider)")
    }

    @Test
    fun `results are sorted alphabetically`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.permissions.map { it.name }).isInOrder()
        assertThat(result.activities.map { it.name }).isInOrder()
        assertThat(result.services.map { it.name }).isInOrder()
    }
}
