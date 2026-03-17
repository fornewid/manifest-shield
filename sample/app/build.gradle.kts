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
    configuration("release") {
        permissions = true
        activities = true
        services = true
        receivers = true
        providers = true
        features = true
        tree = true
    }
}
