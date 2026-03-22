package io.github.fornewid.gradle.plugins.manifestshield.internal.utils

import org.gradle.api.Task

internal object Tasks {
    fun Task.declareCompatibilities() {
        doNotTrackState("This task only outputs to console")
        notCompatibleWithConfigurationCache("Lambda property (allowedFilter) is not serializable")
    }
}
