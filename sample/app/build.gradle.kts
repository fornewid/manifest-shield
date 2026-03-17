plugins {
    id("com.android.application")
    id("io.github.fornewid.manifest-guard")
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 23
        targetSdk = 31
    }
}

dependencies {
    implementation(project(":sample:module1"))
    implementation(libs.androidx.activity)
}

manifestGuard {
    // All dependencies included in Production Release APK
    configuration("releaseRuntimeClasspath") {
        modules = true
        tree = true
        allowedFilter = {
            // Disallow dependencies with a name containing "test"
            !it.contains("test")
        }
    }
}