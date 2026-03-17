package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.models.ComponentType
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestFeature
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestPermission
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal data class ManifestExtraction(
    val permissions: List<ManifestPermission>,
    val activities: List<ManifestComponent>,
    val services: List<ManifestComponent>,
    val receivers: List<ManifestComponent>,
    val providers: List<ManifestComponent>,
    val features: List<ManifestFeature>,
)

internal object ManifestVisitor {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parse(manifestFile: File): ManifestExtraction {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(manifestFile)
        val root = doc.documentElement

        val permissions = root.getElementsByTagName("uses-permission")
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) ManifestPermission(name) else null
            }
            .distinct()
            .sortedBy { it.name }

        val features = root.getElementsByTagName("uses-feature")
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) {
                    val required = node.getAttributeNS(ANDROID_NS, "required")
                    ManifestFeature(
                        name = name,
                        required = required != "false",
                    )
                } else null
            }
            .distinct()
            .sortedBy { it.name }

        val applicationNodes = root.getElementsByTagName("application")
        val application = if (applicationNodes.length > 0) applicationNodes.item(0) as Element else null

        val activities = application?.parseComponents(ComponentType.ACTIVITY).orEmpty()
        val services = application?.parseComponents(ComponentType.SERVICE).orEmpty()
        val receivers = application?.parseComponents(ComponentType.RECEIVER).orEmpty()
        val providers = application?.parseComponents(ComponentType.PROVIDER).orEmpty()

        return ManifestExtraction(
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            features = features,
        )
    }

    private fun Element.parseComponents(type: ComponentType): List<ManifestComponent> {
        return getElementsByTagName(type.tagName)
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) {
                    val exportedAttr = node.getAttributeNS(ANDROID_NS, "exported")
                    ManifestComponent(
                        name = name,
                        type = type,
                        exported = when (exportedAttr) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        },
                    )
                } else null
            }
            .distinct()
            .sortedBy { it.name }
    }

    private fun NodeList.toElementList(): List<Element> {
        return (0 until length).mapNotNull { item(it) as? Element }
    }
}
