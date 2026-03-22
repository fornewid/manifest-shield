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
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(11)
}

kotlin {
  explicitApi()
}

gradlePlugin {
  plugins {
    plugins.create("manifest-shield") {
      id = "io.github.fornewid.manifest-shield"
      implementationClass = "io.github.fornewid.gradle.plugins.manifestshield.ManifestShieldPlugin"
    }
  }
}

// Load signing properties from release/signing.properties (created by signing-setup.sh in CI).
// This included build is not affected by the root project's subprojects {} block.
val signingPropsFile = rootProject.file("../release/signing.properties")
if (signingPropsFile.exists()) {
  val signingProps = java.util.Properties().apply {
    signingPropsFile.inputStream().use { load(it) }
  }
  signingProps.forEach { key, value ->
    val k = key.toString()
    val v = if (k == "signing.secretKeyRingFile") {
      rootProject.file("../$value").absolutePath
    } else {
      value.toString()
    }
    ext.set(k, v)
  }
}

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  signAllPublications()
}

dependencies {
  compileOnly(gradleApi())
  compileOnly("com.android.tools.build:gradle:8.0.0")
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