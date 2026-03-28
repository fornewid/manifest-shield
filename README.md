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

Add the plugin to your **application** module's `build.gradle.kts` (library modules are not supported):

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("io.github.fornewid.manifest-shield") version "<latest-version>"
}

manifestShield {
    configuration("release")
}
```

### Step 2: Generate a baseline

```bash
./gradlew manifestShieldBaseline
```

This creates baseline files in the `manifestShield/` directory:

```
manifestShield/
└── releaseAndroidManifest.txt
```

### Step 3: Detect changes

```bash
./gradlew manifestShield
```

If the merged manifest differs from the baseline, the build fails:

```diff
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

**releaseAndroidManifest.sources.txt** — grouped by source module/library, generated when `sources = true`:

```kotlin
manifestShield {
    configuration("release") {
        sources = true
    }
}
```

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

```kotlin
manifestShield {
    configuration("release") {
        // Enable additional categories
        metaData = true
        intentFilter = true

        // Disable default categories
        exportedOnly = false
        requiredOnly = false
    }
}
```

| Option | Default | Description |
|--------|---------|-------------|
| `baselineDir` | `"manifestShield"` | Directory name for baseline files |
| `sources` | `false` | Enable source-attributed format grouped by library/module |
| `usesSdk` | `false` | Shield `<uses-sdk>` |
| `usesPermission` | **`true`** | Shield `<uses-permission>` |
| `permission` | `false` | Shield `<permission>` |
| `usesFeature` | **`true`** | Shield `<uses-feature>` |
| `activity` | **`true`** | Shield `<activity>` |
| `activityAlias` | **`true`** | Shield `<activity-alias>` |
| `service` | **`true`** | Shield `<service>` |
| `receiver` | **`true`** | Shield `<receiver>` |
| `provider` | **`true`** | Shield `<provider>` |
| `intentFilter` | `false` | Shield `<intent-filter>` on components |
| `startup` | `false` | Shield `androidx.startup` initializers |
| `usesPermissionSdk23` | `false` | Shield `<uses-permission-sdk-23>` |
| `supportsScreens` | `false` | Shield `<supports-screens>` |
| `compatibleScreens` | `false` | Shield `<compatible-screens>` |
| `usesConfiguration` | `false` | Shield `<uses-configuration>` |
| `supportsGlTexture` | `false` | Shield `<supports-gl-texture>` |
| `queries` | `false` | Shield `<queries>` |
| `metaData` | `false` | Shield `<meta-data>` (non-primitive values shown as `(REDACTED)`) |
| `usesLibrary` | `false` | Shield `<uses-library>` |
| `usesNativeLibrary` | `false` | Shield `<uses-native-library>` |
| `profileable` | `false` | Shield `<profileable>` |
| `exportedOnly` | **`true`** | Only include exported components in baseline |
| `requiredOnly` | **`true`** | Only include required `<uses-feature>` and `<uses-library>` entries |

**Note on `exportedOnly`**: Starting with Android 12, components with `<intent-filter>` must explicitly declare `android:exported`. When `exportedOnly = true` (default), only components with `android:exported="true"` are included — components without the attribute are excluded. This aligns with Android 12+ requirements where all intent-filter components must be explicit.

**Note on `intentFilter`**: Intent-filters are never merged between manifests — each source's intent-filter is added independently. When `intentFilter = true`, changes from any dependency update that modifies intent-filters will appear in the baseline diff.

The following manifest elements are **not tracked** by this plugin:

- `<permission-group>`, `<permission-tree>` — rarely used in practice
- `<instrumentation>` — test-only, not present in release builds
- `<property>` — Android 12+ element, may be supported in a future version

## Requirements

- Android Gradle Plugin 8.0.0+
- Gradle 8.0+

## AI Agent Guide

If you use an AI coding assistant (Claude Code, Copilot, Gemini, Cursor, etc.),
reference the [setup guide](docs/setup-guide.md.txt) for accurate installation
instructions and common pitfalls.

## Acknowledgements

Inspired by [dependency-guard](https://github.com/dropbox/dependency-guard) and [manifest-guard](https://github.com/int02h/manifest-guard).

## License

[Apache License 2.0](LICENSE)
