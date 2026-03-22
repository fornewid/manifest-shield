# manifest-shield

A Gradle plugin that shields against unintentional `AndroidManifest.xml` changes.

## Features

- Detects changes in permissions, features, components, and more from the merged manifest
- Generates a single baseline file per variant for easy review
- Supports **tree format** with library attribution via AGP's manifest merge blame log
- Configurable per-category tracking with boolean flags

## Setup

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application")
    id("io.github.fornewid.manifest-shield")
}

manifestShield {
    configuration("release") {
        // These are enabled by default
        sdk = true
        permissions = true
        features = true
        activities = true
        services = true
        // ...

        // These are disabled by default (opt-in)
        metaData = true
        usesLibrary = true
        queries = true
        // ...

        sources = true
    }
}
```

## Usage

```bash
# Save current manifest as baseline
./gradlew manifestShieldBaseline

# Check for manifest changes
./gradlew manifestShield
```

## Baseline Files

Baseline files are stored in the `manifestShield/` directory (configurable via `baselineDir`):

```
manifestShield/
├── releaseAndroidManifest.txt
└── releaseAndroidManifest.sources.txt   # when tree=true
```

### Example Output

**releaseAndroidManifest.txt**
```
<manifest>
uses-sdk:
  minSdkVersion=23
  targetSdkVersion=35

uses-feature:
  android.hardware.camera (required)

uses-permission:
  android.permission.CAMERA
  android.permission.INTERNET
  android.permission.WRITE_EXTERNAL_STORAGE (maxSdkVersion=29)

permission:
  com.example.app.CUSTOM_PERMISSION (protectionLevel=signature)

<application>
activity:
  com.example.app.MainActivity (exported)
    intent-filter:
      action: android.intent.action.MAIN
      category: android.intent.category.LAUNCHER
    intent-filter:
      action: android.intent.action.VIEW
      category: android.intent.category.DEFAULT
      category: android.intent.category.BROWSABLE
      data: https://example.com/content/*

activity-alias:
  com.example.app.ShortcutAlias (exported) -> com.example.app.MainActivity

meta-data:
  com.google.android.geo.API_KEY (REDACTED)
  com.example.app.FEATURE_ENABLED (true)

service:
  com.example.app.MyService

receiver:
  com.example.app.BootReceiver (exported)
    intent-filter:
      action: android.intent.action.BOOT_COMPLETED

provider:
  com.example.app.MyContentProvider (exported, authorities=com.example.app.provider)

uses-library:
  org.apache.http.legacy (required)

androidx.startup:
  com.example.app.MyInitializer
```

**releaseAndroidManifest.sources.txt**
```
[:app]
<manifest>
uses-sdk:
  minSdkVersion=23
  targetSdkVersion=35

uses-permission:
  android.permission.INTERNET

<application>
activity:
  com.example.app.MainActivity (exported)
    intent-filter:
      action: android.intent.action.MAIN
      category: android.intent.category.LAUNCHER

[com.google.firebase:firebase-core:21.0.0]
<manifest>
uses-permission:
  android.permission.CAMERA

<application>
activity:
  com.google.firebase.FirebaseActivity
```

Empty categories are omitted from the output.

## Supported Manifest Elements

| Level | Element | Tracked Attributes |
|---|---|---|
| `<manifest>` | `uses-sdk` | `minSdkVersion`, `targetSdkVersion` |
| | `uses-feature` | `name`, `glEsVersion`, `required` |
| | `uses-permission` | `name`, `maxSdkVersion` |
| | `uses-permission-sdk-23` | `name`, `maxSdkVersion` |
| | `permission` | `name`, `protectionLevel` |
| | `supports-screens` | screen size booleans, width thresholds |
| | `compatible-screens` | screenSize + screenDensity pairs |
| | `uses-configuration` | input hardware requirements |
| | `supports-gl-texture` | `name` |
| | `queries` | package, intent, provider children |
| `<application>` | `activity` | `name`, `exported`, `intent-filter` |
| | `activity-alias` | `name`, `exported`, `targetActivity` |
| | `meta-data` | `name`, `value` (REDACTED for non-primitive) |
| | `service` | `name`, `exported`, `intent-filter` |
| | `receiver` | `name`, `exported`, `intent-filter` |
| | `provider` | `name`, `exported`, `authorities` |
| | `uses-library` | `name`, `required` |
| | `uses-native-library` | `name`, `required` |
| | `profileable` | `shell`, `enabled` |
| | `androidx.startup` | Initializer class names |

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `baselineDir` | `"manifestShield"` | Directory name for baseline files |
| `sdk` | `false` | Shield `<uses-sdk>` |
| `permissions` | **`true`** | Shield `<uses-permission>` |
| `permissionDeclarations` | `false` | Shield `<permission>` |
| `features` | **`true`** | Shield `<uses-feature>` |
| `activities` | **`true`** | Shield `<activity>` |
| `activityAliases` | `false` | Shield `<activity-alias>` |
| `services` | **`true`** | Shield `<service>` |
| `receivers` | **`true`** | Shield `<receiver>` |
| `providers` | **`true`** | Shield `<provider>` |
| `intentFilters` | **`true`** | Shield `<intent-filter>` on components |
| `startup` | **`true`** | Shield `androidx.startup` initializers |
| `permissionsSdk23` | `false` | Shield `<uses-permission-sdk-23>` |
| `supportsScreens` | `false` | Shield `<supports-screens>` |
| `compatibleScreens` | `false` | Shield `<compatible-screens>` |
| `usesConfiguration` | `false` | Shield `<uses-configuration>` |
| `supportsGlTexture` | `false` | Shield `<supports-gl-texture>` |
| `queries` | `false` | Shield `<queries>` |
| `metaData` | `false` | Shield `<meta-data>` (non-primitive values shown as `(REDACTED)`) |
| `usesLibrary` | `false` | Shield `<uses-library>` |
| `usesNativeLibrary` | `false` | Shield `<uses-native-library>` |
| `profileable` | `false` | Shield `<profileable>` |
| `sources` | `false` | Enable source-attributed format grouped by library/module |
| `allowedFilter` | `{ true }` | Filter to allow/disallow entries |
| `baselineMap` | `{ it }` | Transform entries in baseline |

## Requirements

- Android Gradle Plugin 7.1.0+
- Gradle 7.x+

## License

```
Copyright 2024 fornewid

Licensed under the Apache License, Version 2.0
```
