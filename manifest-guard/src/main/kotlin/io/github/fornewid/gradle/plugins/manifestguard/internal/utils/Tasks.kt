package io.github.fornewid.gradle.plugins.manifestguard.internal.utils

import org.gradle.api.Task

internal object Tasks {
    fun Task.declareCompatibilities() {
        doNotTrackState("This task only outputs to console")
        notCompatibleWithConfigurationCache("Lambda properties (allowedFilter, baselineMap) are not serializable")
    }
}
