# :shield: Manifest Shield

[![Maven Central](https://img.shields.io/maven-central/v/io.github.fornewid.manifest-shield/manifest-shield)](https://central.sonatype.com/artifact/io.github.fornewid.manifest-shield/manifest-shield)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fornewid.manifest-shield)](https://plugins.gradle.org/plugin/io.github.fornewid.manifest-shield)
[![Build](https://github.com/fornewid/manifest-shield/actions/workflows/build.yml/badge.svg)](https://github.com/fornewid/manifest-shield/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/fornewid/manifest-shield)](LICENSE)

> :warning: This project is in an experimental stage. APIs and behavior may change without notice.

A Gradle plugin that detects unintentional changes to Android's merged `AndroidManifest.xml`.

## Why?

Android's final `AndroidManifest.xml` is the result of merging manifests from your app, libraries, and SDKs.
When you update a dependency or add a module, unexpected permissions or components can silently appear in your app.

**Manifest Shield** saves a baseline of your merged manifest and fails the build when something changes — so you always know what's in your app.

Based on the [App Manifest](https://developer.android.com/guide/topics/manifest/manifest-intro) structure defined by Android.

## Quick Start

### Step 1: Apply the plugin

Add the plugin to your module's `build.gradle.kts`:

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") // or id("com.android.library")
    id("io.github.fornewid.manifest-shield") version "<latest-version>"
}

manifestShield {
    configuration("release") {
        sources = true       // Enable source-attributed format
        metaData = true      // Opt-in to track <meta-data>
    }
}
```

### Step 2: Generate a baseline

```bash
./gradlew manifestShieldBaseline
```

This creates baseline files in the `manifestShield/` directory:

```
manifestShield/
├── releaseAndroidManifest.txt
└── releaseAndroidManifest.sources.txt   # when sources = true
```

### Step 3: Detect changes

```bash
./gradlew manifestShield
```

If the merged manifest differs from the baseline, the build fails:

```
Manifest Changed in :app for release/list
- android.permission.WRITE_EXTERNAL_STORAGE
+ android.permission.WRITE_EXTERNAL_STORAGE (maxSdkVersion=28)
+ android.permission.ACCESS_FINE_LOCATION

If this is intentional, re-baseline using ./gradlew :app:manifestShieldBaselineRelease
Or use ./gradlew manifestShieldBaseline to re-baseline in entire project.
```

## Output

**releaseAndroidManifest.txt** — flat list of all manifest entries:

```
uses-feature:
  android.hardware.camera

uses-permission:
  android.permission.ACCESS_NETWORK_STATE
  android.permission.INTERNET

activity:
  io.github.fornewid.manifest.shield.sample.app.MainActivity (exported)

service:
  io.github.fornewid.manifest.shield.sample.app.BoundService (exported)
    permission: android.permission.BIND_JOB_SERVICE
```

Permission attributes (`android:permission`, `android:readPermission`, `android:writePermission`) are shown as indented lines below exported components.

**releaseAndroidManifest.sources.txt** — grouped by source module/library (when `sources = true`):

```
[:sample:app]
uses-feature:
  android.hardware.camera

uses-permission:
  android.permission.INTERNET

activity:
  io.github.fornewid.manifest.shield.sample.app.MainActivity (exported)

[:sample:module1]
uses-permission:
  android.permission.ACCESS_NETWORK_STATE
  android.permission.INTERNET
```

Empty categories are omitted from the output.

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `baselineDir` | `"manifestShield"` | Directory name for baseline files |
| `sources` | `false` | Enable source-attributed format grouped by library/module |
| `sdk` | `false` | Shield `<uses-sdk>` |
| `permissions` | **`true`** | Shield `<uses-permission>` |
| `permission` | `false` | Shield `<permission>` |
| `features` | **`true`** | Shield `<uses-feature>` |
| `activities` | **`true`** | Shield `<activity>` |
| `activityAlias` | `false` | Shield `<activity-alias>` |
| `services` | **`true`** | Shield `<service>` |
| `receivers` | **`true`** | Shield `<receiver>` |
| `providers` | **`true`** | Shield `<provider>` |
| `intentFilter` | `false` | Shield `<intent-filter>` on components |
| `startup` | **`true`** | Shield `androidx.startup` initializers |
| `usesPermissionSdk23` | `false` | Shield `<uses-permission-sdk-23>` |
| `supportsScreens` | `false` | Shield `<supports-screens>` |
| `compatibleScreens` | `false` | Shield `<compatible-screens>` |
| `usesConfiguration` | `false` | Shield `<uses-configuration>` |
| `supportsGlTexture` | `false` | Shield `<supports-gl-texture>` |
| `queries` | **`true`** | Shield `<queries>` |
| `metaData` | `false` | Shield `<meta-data>` (non-primitive values shown as `(REDACTED)`) |
| `usesLibrary` | `false` | Shield `<uses-library>` |
| `usesNativeLibrary` | `false` | Shield `<uses-native-library>` |
| `profileable` | `false` | Shield `<profileable>` |
| `exportedOnly` | **`true`** | Only include exported components in baseline |
| `requiredOnly` | **`true`** | Only include required `<uses-feature>` and `<uses-library>` entries |

## Requirements

- Android Gradle Plugin 8.0.0+
- Gradle 8.0+

## Acknowledgements

Inspired by [dependency-guard](https://github.com/dropbox/dependency-guard) and [manifest-guard](https://github.com/int02h/manifest-guard).

## License

[Apache License 2.0](LICENSE)
