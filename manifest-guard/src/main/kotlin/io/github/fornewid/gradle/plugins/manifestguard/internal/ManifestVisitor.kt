package io.github.fornewid.gradle.plugins.manifestguard.internal

import io.github.fornewid.gradle.plugins.manifestguard.models.ComponentType
import io.github.fornewid.gradle.plugins.manifestguard.models.IntentFilterInfo
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestComponent
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestFeature
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestPermission
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestPermissionDeclaration
import io.github.fornewid.gradle.plugins.manifestguard.models.ManifestSdk
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal data class ManifestExtraction(
    val sdk: ManifestSdk?,
    val permissions: List<ManifestPermission>,
    val permissionDeclarations: List<ManifestPermissionDeclaration>,
    val activities: List<ManifestComponent>,
    val activityAliases: List<ManifestComponent>,
    val services: List<ManifestComponent>,
    val receivers: List<ManifestComponent>,
    val providers: List<ManifestComponent>,
    val features: List<ManifestFeature>,
    val startupInitializers: List<String>,
)

internal object ManifestVisitor {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parse(manifestFile: File): ManifestExtraction {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(manifestFile)
        val root = doc.documentElement

        val sdk = root.getElementsByTagName("uses-sdk")
            .toElementList()
            .firstOrNull()
            ?.let { node ->
                val minSdk = node.getAttributeNS(ANDROID_NS, "minSdkVersion")
                    .takeIf { it.isNotBlank() }?.toIntOrNull()
                val targetSdk = node.getAttributeNS(ANDROID_NS, "targetSdkVersion")
                    .takeIf { it.isNotBlank() }?.toIntOrNull()
                if (minSdk != null || targetSdk != null) ManifestSdk(minSdk, targetSdk) else null
            }

        val permissions = root.getElementsByTagName("uses-permission")
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) {
                    val maxSdk = node.getAttributeNS(ANDROID_NS, "maxSdkVersion")
                        .takeIf { it.isNotBlank() }?.toIntOrNull()
                    ManifestPermission(name = name, maxSdkVersion = maxSdk)
                } else null
            }
            .distinct()
            .sortedBy { it.name }

        val permissionDeclarations = root.getElementsByTagName("permission")
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) {
                    val protectionLevel = node.getAttributeNS(ANDROID_NS, "protectionLevel")
                        .takeIf { it.isNotBlank() }
                    ManifestPermissionDeclaration(name = name, protectionLevel = protectionLevel)
                } else null
            }
            .distinct()
            .sortedBy { it.name }

        val features = root.getElementsByTagName("uses-feature")
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                val glEsVersion = node.getAttributeNS(ANDROID_NS, "glEsVersion")
                val required = node.getAttributeNS(ANDROID_NS, "required")
                val featureName = when {
                    name.isNotBlank() -> name
                    glEsVersion.isNotBlank() -> "glEsVersion=$glEsVersion"
                    else -> return@mapNotNull null
                }
                ManifestFeature(
                    name = featureName,
                    required = required != "false",
                )
            }
            .distinct()
            .sortedBy { it.name }

        val applicationNodes = root.getElementsByTagName("application")
        val application = if (applicationNodes.length > 0) applicationNodes.item(0) as Element else null

        val activities = application?.parseComponents(ComponentType.ACTIVITY).orEmpty()
        val activityAliases = application?.parseComponents(ComponentType.ACTIVITY_ALIAS).orEmpty()
        val services = application?.parseComponents(ComponentType.SERVICE).orEmpty()
        val receivers = application?.parseComponents(ComponentType.RECEIVER).orEmpty()
        val providers = application?.parseProviders().orEmpty()
        val startupInitializers = application?.parseStartupInitializers().orEmpty()

        return ManifestExtraction(
            sdk = sdk,
            permissions = permissions,
            permissionDeclarations = permissionDeclarations,
            activities = activities,
            activityAliases = activityAliases,
            services = services,
            receivers = receivers,
            providers = providers,
            features = features,
            startupInitializers = startupInitializers,
        )
    }

    private fun Element.parseComponents(type: ComponentType): List<ManifestComponent> {
        return getElementsByTagName(type.tagName)
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) {
                    val exportedAttr = node.getAttributeNS(ANDROID_NS, "exported")
                    val targetActivity = if (type == ComponentType.ACTIVITY_ALIAS) {
                        node.getAttributeNS(ANDROID_NS, "targetActivity").takeIf { it.isNotBlank() }
                    } else null
                    ManifestComponent(
                        name = name,
                        type = type,
                        exported = when (exportedAttr) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        },
                        targetActivity = targetActivity,
                        intentFilters = node.parseIntentFilters(),
                    )
                } else null
            }
            .distinct()
            .sortedBy { it.name }
    }

    private fun Element.parseProviders(): List<ManifestComponent> {
        return getElementsByTagName("provider")
            .toElementList()
            .mapNotNull { node ->
                val name = node.getAttributeNS(ANDROID_NS, "name")
                if (name.isNotBlank()) {
                    val exportedAttr = node.getAttributeNS(ANDROID_NS, "exported")
                    val authorities = node.getAttributeNS(ANDROID_NS, "authorities")
                        .takeIf { it.isNotBlank() }
                    ManifestComponent(
                        name = name,
                        type = ComponentType.PROVIDER,
                        exported = when (exportedAttr) {
                            "true" -> true
                            "false" -> false
                            else -> null
                        },
                        authorities = authorities,
                    )
                } else null
            }
            .distinct()
            .sortedBy { it.name }
    }

    private fun Element.parseStartupInitializers(): List<String> {
        return getElementsByTagName("provider")
            .toElementList()
            .filter { node ->
                node.getAttributeNS(ANDROID_NS, "name") == "androidx.startup.InitializationProvider"
            }
            .flatMap { provider ->
                provider.getElementsByTagName("meta-data")
                    .toElementList()
                    .filter { meta ->
                        meta.getAttributeNS(ANDROID_NS, "value") == "androidx.startup"
                    }
                    .mapNotNull { meta ->
                        meta.getAttributeNS(ANDROID_NS, "name").takeIf { it.isNotBlank() }
                    }
            }
            .sorted()
    }

    private fun Element.parseIntentFilters(): List<IntentFilterInfo> {
        return getElementsByTagName("intent-filter")
            .toElementList()
            .map { filter ->
                val actions = filter.getElementsByTagName("action")
                    .toElementList()
                    .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
                    .sorted()

                val categories = filter.getElementsByTagName("category")
                    .toElementList()
                    .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
                    .sorted()

                val dataSpecs = filter.getElementsByTagName("data")
                    .toElementList()
                    .map { data ->
                        val scheme = data.getAttributeNS(ANDROID_NS, "scheme")
                        val host = data.getAttributeNS(ANDROID_NS, "host")
                        val port = data.getAttributeNS(ANDROID_NS, "port")
                        val path = data.getAttributeNS(ANDROID_NS, "path")
                        val pathPrefix = data.getAttributeNS(ANDROID_NS, "pathPrefix")
                        val pathPattern = data.getAttributeNS(ANDROID_NS, "pathPattern")
                        val mimeType = data.getAttributeNS(ANDROID_NS, "mimeType")
                        buildDataSpec(scheme, host, port, path, pathPrefix, pathPattern, mimeType)
                    }
                    .filter { it.isNotBlank() }
                    .sorted()

                IntentFilterInfo(actions = actions, categories = categories, dataSpecs = dataSpecs)
            }
    }

    private fun buildDataSpec(
        scheme: String, host: String, port: String,
        path: String, pathPrefix: String, pathPattern: String,
        mimeType: String,
    ): String = buildString {
        if (scheme.isNotBlank()) {
            append(scheme)
            append("://")
            if (host.isNotBlank()) {
                append(host)
                if (port.isNotBlank()) append(":$port")
            }
            val pathPart = when {
                path.isNotBlank() -> path
                pathPrefix.isNotBlank() -> "$pathPrefix*"
                pathPattern.isNotBlank() -> pathPattern
                else -> ""
            }
            if (pathPart.isNotBlank()) append(pathPart)
        } else if (host.isNotBlank()) {
            append("//$host")
            if (port.isNotBlank()) append(":$port")
        } else if (mimeType.isNotBlank()) {
            append(mimeType)
        }
    }

    private fun NodeList.toElementList(): List<Element> {
        return (0 until length).mapNotNull { item(it) as? Element }
    }
}
