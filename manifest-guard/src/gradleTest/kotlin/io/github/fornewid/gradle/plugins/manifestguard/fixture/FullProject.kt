package io.github.fornewid.gradle.plugins.manifestguard.fixture

import io.github.fornewid.gradle.plugins.manifestguard.util.createDirectories
import io.github.fornewid.gradle.plugins.manifestguard.util.writeText
import java.nio.file.Path

class FullProject : AbstractProject() {

    private val gradlePropertiesFile = projectDir.resolve("gradle.properties")
    private val settingsFile = projectDir.resolve("settings.gradle")
    private val rootBuildFile = projectDir.resolve("build.gradle")

    // Generate java library project named 'lib'
    val lib = minimalLibrary("lib")

    init {
        gradlePropertiesFile.writeText(
            """
        org.gradle.jvmargs=-Dfile.encoding=UTF-8
      """.trimIndent()
        )

        settingsFile.writeText(
            """
      pluginManagement {
        repositories {
          gradlePluginPortal()
          mavenCentral()
        }
      }
      
      dependencyResolutionManagement {
        repositories {
          mavenCentral()
        }
      }
      
      rootProject.name = 'project-under-test'
      
      // Don't forget to add all your modules here!
      include ':lib'
      """.trimIndent()
        )

        rootBuildFile.writeText(
            """
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath("org.jacoco:org.jacoco.core:0.8.7")
                }
            }
                
            plugins {
                id 'io.github.fornewid.manifest-guard'
            }
            
            manifestGuard {
                configuration('classpath') {
                    tree = true
                }
            }
            """.trimIndent()
        )
    }

    private fun minimalLibrary(name: String): Path {
        val lib = projectDir.resolve(name).createDirectories()

        lib.resolve("build.gradle").writeText(
            """
      plugins {
        id 'java-library'
        id 'io.github.fornewid.manifest-guard'
      }
      
      dependencies {
        implementation 'io.reactivex.rxjava3:rxjava:3.1.4'
        testImplementation 'junit:junit:4.13.2'
      }
      
      manifestGuard {
        configuration('compileClasspath') {
          tree = true
        }
        configuration('testCompileClasspath') {
          tree = true
        }
      }
      """.trimIndent()
        )

        return lib
    }
}
