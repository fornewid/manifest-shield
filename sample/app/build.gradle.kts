plugins {
    alias(libs.plugins.android.application)
    id("io.github.fornewid.manifest-shield")
}

android {
    namespace = "io.github.fornewid.manifest.shield.sample.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
    }
}

dependencies {
    implementation(project(":sample:module1"))
    implementation(libs.androidx.activity)
}

manifestShield {
    configuration("release") {
        sources = true
        metaData = true
    }
}
