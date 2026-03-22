plugins {
    alias(libs.plugins.android.application)
    id("io.github.fornewid.manifest-guard")
}

android {
    namespace = "io.github.fornewid.manifest.guard.sample.app"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        targetSdk = 35
    }
}

dependencies {
    implementation(project(":sample:module1"))
    implementation(libs.androidx.activity)
}

manifestGuard {
    configuration("release") {
        sources = true
        metaData = true
    }
}
