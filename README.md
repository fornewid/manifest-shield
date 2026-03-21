# manifest-guard

A Gradle plugin that guards against unintentional `AndroidManifest.xml` changes.

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
    id("io.github.fornewid.manifest-guard")
}

manifestGuard {
    baselineDir = "manifest" // default
    configuration("release") {
        sdk = true
        permissions = true
        permissionDeclarations = true
        features = true
        activities = true
        activityAliases = true
        services = true
        receivers = true
        providers = true
        intentFilters = true
        startup = true
        tree = true
    }
}
```

## Usage

```bash
# Save current manifest as baseline
./gradlew manifestGuardBaseline

# Check for manifest changes
./gradlew manifestGuard
```

## Baseline Files

Baseline files are stored in the `manifest/` directory (configurable via `baselineDir`):

```
manifest/
├── releaseAndroidManifest.txt
└── releaseAndroidManifest.tree.txt   # when tree=true
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

service:
  com.example.app.MyService

receiver:
  com.example.app.BootReceiver (exported)
    intent-filter:
      action: android.intent.action.BOOT_COMPLETED

provider:
  com.example.app.MyContentProvider (exported, authorities=com.example.app.provider)

androidx.startup:
  com.example.app.MyInitializer
```

**releaseAndroidManifest.tree.txt**
```
[app]
<manifest>
uses-permission:
  android.permission.INTERNET

<application>
activity:
  com.example.app.MainActivity (exported)

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
| | `permission` | `name`, `protectionLevel` |
| `<application>` | `activity` | `name`, `exported`, `intent-filter` |
| | `activity-alias` | `name`, `exported`, `targetActivity` |
| | `service` | `name`, `exported`, `intent-filter` |
| | `receiver` | `name`, `exported`, `intent-filter` |
| | `provider` | `name`, `exported`, `authorities` |
| | `androidx.startup` | Initializer class names |

### Not Supported

| Element | Reason |
|---|---|
| `<meta-data>` | May contain sensitive values (API keys). Visible in `.tree.txt` via full manifest. |
| `<supports-screens>` | Low usage frequency |
| `<compatible-screens>` | Low usage frequency |
| `<uses-configuration>` | Low usage frequency |
| `<supports-gl-texture>` | Low usage frequency |
| `<uses-library>` | Under consideration |
| `<queries>` | Under consideration |

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `baselineDir` | `"manifest"` | Directory name for baseline files |
| `sdk` | `true` | Guard `<uses-sdk>` declarations |
| `permissions` | `true` | Guard `<uses-permission>` declarations |
| `permissionDeclarations` | `true` | Guard `<permission>` declarations |
| `features` | `true` | Guard `<uses-feature>` declarations |
| `activities` | `true` | Guard `<activity>` declarations |
| `activityAliases` | `true` | Guard `<activity-alias>` declarations |
| `services` | `true` | Guard `<service>` declarations |
| `receivers` | `true` | Guard `<receiver>` declarations |
| `providers` | `true` | Guard `<provider>` declarations |
| `intentFilters` | `true` | Guard `<intent-filter>` on exported components |
| `startup` | `true` | Guard `androidx.startup` initializers |
| `tree` | `false` | Enable tree format with library attribution |
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
