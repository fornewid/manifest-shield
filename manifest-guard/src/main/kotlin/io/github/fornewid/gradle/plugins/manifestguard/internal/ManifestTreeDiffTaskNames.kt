package io.github.fornewid.gradle.plugins.manifestguard.internal

internal object ManifestTreeDiffTaskNames {

    /**
     * This extension [org.gradle.configurationcache.extensions.capitalized]
     * is not available until 7.4.x, so this is a backport.
     *
     * Fixes: https://github.com/dropbox/manifest-guard/issues/40
     */
    fun String.capitalized(): String {
        return if (this.isEmpty()) {
            ""
        } else {
            val firstChar = get(0)
            if (firstChar.isUpperCase()) {
                return this
            } else {
                firstChar.toUpperCase() + substring(1)
            }
        }
    }

    fun createManifestTreeTaskNameForConfiguration(configurationName: String): String {
        return "dependencyTreeDiff${configurationName.capitalized()}"
    }

    fun createManifestTreeBaselineTaskNameForConfiguration(configurationName: String): String {
        return "dependencyTreeDiffBaseline${configurationName.capitalized()}"
    }
}
