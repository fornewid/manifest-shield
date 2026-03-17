package io.github.fornewid.gradle.plugins.manifestguard

import io.github.fornewid.gradle.plugins.manifestguard.fixture.Builder.build
import io.github.fornewid.gradle.plugins.manifestguard.fixture.Builder.buildAndFail
import io.github.fornewid.gradle.plugins.manifestguard.fixture.ConfiguredProject
import io.github.fornewid.gradle.plugins.manifestguard.fixture.FullProject
import io.github.fornewid.gradle.plugins.manifestguard.fixture.SimpleProject
import io.github.fornewid.gradle.plugins.manifestguard.util.assertFileExistsWithContentEqual
import io.github.fornewid.gradle.plugins.manifestguard.util.replaceText
import com.google.common.truth.Truth.assertThat

class PluginTest {

    @ParameterizedPluginTest
    fun `can generate baseline`(args: ParameterizedPluginArgs): Unit = SimpleProject(tree = true).use { project ->
        val result = build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        // verify baseline
        assertThat(result.output)
            .contains("Manifest Guard baseline created for :lib for configuration compileClasspath.")
        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "simple/list_before_update.txt",
        )
        // verify tree baseline
        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.tree.txt",
            contentFile = "simple/tree_before_update.txt",
        )

        if (args.withConfigurationCache) {
            // verify configuration-cache related stuff
            assertThat(result.output)
                .contains("Calculating task graph as no configuration cache is available for tasks: :lib:manifestGuard")
            assertThat(result.output)
                .contains("Configuration cache entry stored.")
        }
    }

    @ParameterizedPluginTest
    fun `guard with no dependencies changes`(
        args: ParameterizedPluginArgs,
    ): Unit = SimpleProject(tree = true).use { project ->
        // create baseline
        build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        // check with no dependencies changes
        val result = build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        assertThat(result.output)
            .doesNotContain("No Manifest Changes Found in :lib for configuration \"compileClasspath\"")

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "simple/list_before_update.txt",
        )

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.tree.txt",
            contentFile = "simple/tree_before_update.txt",
        )
    }

    @ParameterizedPluginTest
    fun `guard with no dependencies changes - verbose`(
        args: ParameterizedPluginArgs,
    ): Unit = SimpleProject(tree = true).use { project ->
        // create baseline
        build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        // check with no dependencies changes
        val result = build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard", "--debug")
        )

        assertThat(result.output)
            .contains("No Manifest Changes Found in :lib for configuration \"compileClasspath\"")

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "simple/list_before_update.txt",
        )

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.tree.txt",
            contentFile = "simple/tree_before_update.txt",
        )
    }

    @ParameterizedPluginTest
    fun `guard after dependencies tree changes`(
        args: ParameterizedPluginArgs,
    ): Unit = SimpleProject(tree = true).use { project ->
        // create baseline
        build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        project.lib.resolve("build.gradle").replaceText(
            oldValue = "implementation 'io.reactivex.rxjava3:rxjava:3.1.4'",
            newValue = "implementation 'io.reactivex.rxjava3:rxjava:3.1.5'",
        )

        // check after dependencies changes
        val result = buildAndFail(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        assertThat(result.output).contains("Manifest Tree comparison to baseline does not match.")
        assertThat(result.output).contains(
            """
    [33m***** DEPENDENCY CHANGE DETECTED *****[0m
    [31m-\--- io.reactivex.rxjava3:rxjava:3.1.4[0m
    [31m-     \--- org.reactivestreams:reactive-streams:1.0.3[0m
    [32m+\--- io.reactivex.rxjava3:rxjava:3.1.5[0m
    [32m+     \--- org.reactivestreams:reactive-streams:1.0.4[0m
        """.trimIndent()
        )

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "simple/list_before_update.txt",
        )

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.tree.txt",
            contentFile = "simple/tree_before_update.txt",
        )
    }

    @ParameterizedPluginTest
    fun `guard after dependencies list changes`(
        args: ParameterizedPluginArgs,
    ): Unit = SimpleProject(tree = false).use { project ->
        // create baseline
        build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        project.lib.resolve("build.gradle").replaceText(
            oldValue = "implementation 'io.reactivex.rxjava3:rxjava:3.1.4'",
            newValue = "implementation 'io.reactivex.rxjava3:rxjava:3.1.5'",
        )

        // check after dependencies changes
        val result = buildAndFail(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        assertThat(result.output)
            .contains(
                """
                > Dependencies Changed in :lib for configuration compileClasspath
                  - io.reactivex.rxjava3:rxjava:3.1.4
                  + io.reactivex.rxjava3:rxjava:3.1.5
                  - org.reactivestreams:reactive-streams:1.0.3
                  + org.reactivestreams:reactive-streams:1.0.4
                  
                  If this is intentional, re-baseline using ./gradlew :lib:manifestGuardBaseline
            """.trimIndent()
            )

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "simple/list_before_update.txt",
        )
    }

    @ParameterizedPluginTest
    fun `baseline after dependencies changes`(
        args: ParameterizedPluginArgs,
    ): Unit = SimpleProject(tree = true).use { project ->
        // create baseline
        build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        project.lib.resolve("build.gradle").replaceText(
            oldValue = "implementation 'io.reactivex.rxjava3:rxjava:3.1.4'",
            newValue = "implementation 'io.reactivex.rxjava3:rxjava:3.1.5'",
        )

        // check after dependencies changes
        val result = build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuardBaseline")
        )

        assertThat(result.output)
            .contains("Manifest Guard baseline created for :lib for configuration compileClasspath")

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "simple/list_after_update.txt",
        )

        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.tree.txt",
            contentFile = "simple/tree_after_update.txt",
        )
    }

    @ParameterizedPluginTest
    fun `can generate full report baseline`(args: ParameterizedPluginArgs): Unit = FullProject().use { project ->
        val result = build(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf("manifestGuard")
        )

        // verify baseline
        assertThat(result.output).contains("Manifest Guard baseline created for : for configuration classpath.")
        assertThat(result.output).contains("Manifest Guard baseline created for :lib for configuration compileClasspath.")
        assertThat(result.output).contains("Manifest Guard baseline created for :lib for configuration testCompileClasspath.")
        assertThat(result.output).contains("Manifest Guard Tree baseline created for : for configuration classpath.")
        assertThat(result.output).contains("Manifest Guard Tree baseline created for :lib for configuration compileClasspath.")
        assertThat(result.output).contains("Manifest Guard Tree baseline created for :lib for configuration testCompileClasspath.")

        project.assertFileExistsWithContentEqual(
            filename = "dependencies/classpath.txt",
            contentFile = "full/root_list.txt",
        )
        project.assertFileExistsWithContentEqual(
            filename = "dependencies/classpath.tree.txt",
            contentFile = "full/root_tree.txt",
        )
        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.txt",
            contentFile = "full/lib_compile_list.txt",
        )
        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/compileClasspath.tree.txt",
            contentFile = "full/lib_compile_tree.txt",
        )
        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/testCompileClasspath.txt",
            contentFile = "full/lib_test_compile_list.txt",
        )
        project.assertFileExistsWithContentEqual(
            filename = "lib/dependencies/testCompileClasspath.tree.txt",
            contentFile = "full/lib_test_compile_tree.txt",
        )

        if (args.withConfigurationCache) {
            // verify configuration cache is supported
            assertThat(result.output).contains("Calculating task graph as no configuration cache is available for tasks: manifestGuard")
            assertThat(result.output).contains("Configuration cache entry stored.")
        }
    }

    @ParameterizedPluginTest
    fun `prints error when no configuration found`(args: ParameterizedPluginArgs): Unit = ConfiguredProject(
        configurations = emptyList(),
    ).use { project ->
        val result = buildAndFail(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        assertThat(result.output).contains("Error: No configurations provided to Manifest Guard Plugin for project :lib")
    }

    @ParameterizedPluginTest
    fun `prints error when wrong configuration found`(args: ParameterizedPluginArgs): Unit = ConfiguredProject(
        configurations = listOf(
            "compileClasspath",
            "releaseCompileClasspath",
        ),
    ).use { project ->
        val result = buildAndFail(
            gradleVersion = args.gradleVersion,
            withConfigurationCache = args.withConfigurationCache,
            project = project,
            args = arrayOf(":lib:manifestGuard")
        )

        assertThat(result.output).contains("Manifest Guard could not resolve the configurations named [releaseCompileClasspath] for :lib")
    }
}
