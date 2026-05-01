package io.github.fornewid.gradle.plugins.manifestshield.models

/**
 * One `<intent>` child of a `<queries>` block.
 *
 * Unlike [IntentFilterInfo] (used by `<activity>` etc.), this carries the
 * synthesized AGP manifest-merger blame-log key so that the source-grouped
 * baseline can attribute each intent to the module or library that injected
 * it. AGP keys queries-level intents with a composite of the form
 * `intent#action:name:$action[+category:name:$cat][+data:$attr:$value]`.
 */
internal data class QueryIntent(
    val actions: List<String>,
    val categories: List<String>,
    val dataSpecs: List<String>,
    val blameKey: String,
)
