package io.github.fornewid.gradle.plugins.manifestguard.util

import io.github.fornewid.gradle.plugins.manifestguard.fixture.AbstractProject
import com.google.common.truth.Truth.assertThat
import java.io.File

fun AbstractProject.assertFileExistsWithContentEqual(
    filename: String,
    contentFile: String,
) {
    val path = projectFile(filename)
    assertThat(path.exists()).isTrue()

    val actualContent = path.readText()
    val expectedContent: String = File("src/gradleTest/resources/$contentFile").readText()
    assertThat(actualContent).isEqualTo(expectedContent)
}