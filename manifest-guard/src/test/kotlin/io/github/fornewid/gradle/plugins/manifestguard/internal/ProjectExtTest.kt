package io.github.fornewid.gradle.plugins.manifestguard.internal

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ProjectExtTest {

    @Test
    fun `not root project qualified baseline task`() {
        assertThat(getQualifiedBaselineTaskForProjectPath(":sample:app"))
            .isEqualTo(":sample:app:manifestGuardBaseline")
    }

    @Test
    fun `root project qualified baseline task`() {
        assertThat(getQualifiedBaselineTaskForProjectPath(":"))
            .isEqualTo(":manifestGuardBaseline")
    }
}