import com.vanniktech.maven.publish.SonatypeHost.S01
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.binaryCompatibilityValidator)
}

repositories {
  mavenCentral()
  google()
  gradlePluginPortal()
}

val VERSION_NAME: String by project
version = VERSION_NAME

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = "1.8"
    // Because Gradle's Kotlin handling, this falls out of date quickly
    apiVersion = "1.4"
    languageVersion = "1.4"

    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += "-Xsam-conversions=class"
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(8)
}

kotlin {
  explicitApi()
}

gradlePlugin {
  plugins {
    plugins.create("manifest-guard") {
      id = "io.github.fornewid.manifest-guard"
      implementationClass = "io.github.fornewid.gradle.plugins.manifestguard.ManifestGuardPlugin"
    }
  }
}

mavenPublish {
  sonatypeHost = S01
}

dependencies {
  compileOnly(gradleApi())
  compileOnly("com.android.tools.build:gradle:7.1.0")
}

@Suppress("UnstableApiUsage")
testing {
  suites {
    val test by getting(JvmTestSuite::class) {
      useJUnitJupiter()
      dependencies {
        implementation(libs.truth)
      }
    }
  }
}

tasks.register("printVersionName") {
  doLast {
    println(VERSION_NAME)
  }
}