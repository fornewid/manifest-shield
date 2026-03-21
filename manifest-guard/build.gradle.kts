import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.binary.compatibility.validator)
}

repositories {
  mavenCentral()
  google()
  gradlePluginPortal()
}

val VERSION_NAME: String by project
version = VERSION_NAME

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_4)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_4)
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

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  signAllPublications()
}

dependencies {
  compileOnly(gradleApi())
  compileOnly("com.android.tools.build:gradle:7.1.0")
}

val deleteOldGradleTests = tasks.register<Delete>("deleteOldGradleTests") {
  delete(layout.buildDirectory.file("gradleTest"))
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

    val gradleTest by registering(JvmTestSuite::class) {
      useJUnitJupiter()
      dependencies {
        implementation(project())
        implementation(libs.truth)
      }

      targets {
        configureEach {
          testTask.configure {
            shouldRunAfter(test)
            dependsOn(deleteOldGradleTests)
            maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
          }
        }
      }
    }
  }
}

gradlePlugin.testSourceSets(sourceSets.named("gradleTest").get())

// Pass the plugin JAR path to gradleTest for buildscript classpath injection.
// We avoid withPluginClasspath() due to AGP classloader isolation issues.
afterEvaluate {
  val jarTask = tasks.named("jar", Jar::class.java).get()
  tasks.named("gradleTest", Test::class.java) {
    dependsOn(jarTask)
    systemProperty("pluginJar", jarTask.archiveFile.get().asFile.absolutePath)
  }
}

@Suppress("UnstableApiUsage")
tasks.named("check") {
  dependsOn(testing.suites.named("gradleTest"))
}

tasks.register("printVersionName") {
  doLast {
    println(VERSION_NAME)
  }
}