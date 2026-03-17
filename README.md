# manifest-guard

A Gradle plugin that guards against unintentional `AndroidManifest.xml` changes.

## Features

- Detects changes in **permissions**, **activities**, **services**, **receivers**, **providers**, and **uses-feature** declarations
- Generates per-category baseline files for easy review
- Supports **tree format** with library attribution via AGP's manifest merge blame log
- Annotations: `(exported)` for components, `(required)` / `(not-required)` for features

## Setup

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application")
    id("io.github.fornewid.manifest-guard")
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
```

## Usage

```bash
# Save current manifest as baseline
./gradlew :app:manifestGuardBaselineRelease

# Check for manifest changes
./gradlew :app:manifestGuardRelease
```

## Baseline Files

Baseline files are stored in `manifest-guard/<variant>/` directory:

```
manifest-guard/release/
├── permissions.txt
├── activities.txt
├── services.txt
├── receivers.txt
├── providers.txt
├── features.txt
├── permissions.tree.txt   # when tree=true
├── activities.tree.txt
└── ...
```

### Example Output

**permissions.txt**
```
android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET
android.permission.WAKE_LOCK
```

**activities.txt**
```
com.example.app.DetailActivity
com.example.app.MainActivity (exported)
com.google.firebase.FirebaseActivity
```

**activities.tree.txt**
```
app:
  com.example.app.DetailActivity
  com.example.app.MainActivity (exported)
com.google.firebase:firebase-core:21.0.0:
  com.google.firebase.FirebaseActivity
```

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `permissions` | `true` | Guard `<uses-permission>` declarations |
| `activities` | `true` | Guard `<activity>` declarations |
| `services` | `true` | Guard `<service>` declarations |
| `receivers` | `true` | Guard `<receiver>` declarations |
| `providers` | `true` | Guard `<provider>` declarations |
| `features` | `true` | Guard `<uses-feature>` declarations |
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
