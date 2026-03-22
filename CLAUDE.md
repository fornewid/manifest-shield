# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the plugin
./gradlew :manifest-shield:compileKotlin

# Run unit tests only
./gradlew :manifest-shield:test

# Run integration tests (GradleRunner-based, requires ANDROID_HOME or local.properties)
./gradlew :manifest-shield:gradleTest

# Run all tests + API compatibility check
./gradlew :manifest-shield:check

# Regenerate API dump after public API changes
./gradlew :manifest-shield:apiDump

# Generate sample baselines
./gradlew manifestShieldBaseline

# Check sample manifests against baselines
./gradlew manifestShield
```

Note: CI uses JDK 17 (Zulu). Locally, Android Studio's bundled JDK works. If `JAVA_HOME` is not set, use:
```bash
JAVA_HOME="/path/to/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...
```

## Architecture

This is a Gradle plugin (`io.github.fornewid.manifest-shield`) that detects unintentional changes to Android's merged `AndroidManifest.xml`.

### Module Structure

- `manifest-shield/` — The publishable Gradle plugin (included build)
- `sample/app/` — Android app demonstrating the plugin
- `sample/module1/` — Android library with its own manifest (tests multi-module attribution)

### Key Components

**Plugin entry**: `ManifestShieldPlugin` → registers `manifestShield` / `manifestShieldBaseline` tasks via `AndroidVariantHandler` which hooks into AGP's `onVariants`.

**Two task types**:
- `ManifestShieldListTask` — Generates `releaseAndroidManifest.txt` (flat baseline)
- `ManifestSourcesDiffTask` — Generates `releaseAndroidManifest.sources.txt` (grouped by source module/library, opt-in via `sources = true`)

**Shared configuration**: `ShieldFlags` interface + `applyConfig()` extension eliminates duplicate `@Input` properties between the two tasks. `EnabledCategories` data class replaces magic-string maps for type safety.

**Parsing pipeline**:
1. `ManifestVisitor` — DOM XML parsing of merged manifest → `ManifestExtraction` (20+ element types)
2. `BlameLogParser` — Parses AGP's `manifest-merger-<variant>-report.txt` blame log → `Map<String, List<String>>` (element → list of sources). Handles ADDED, INJECTED, MERGED, IMPLIED, CONVERTED actions. Resolves module paths from file paths using project root dir.

**Output format**: Sections grouped by `<manifest>` and `<application>` level, ordered per Android documentation. Empty categories are omitted. Intent-filters are nested under their parent component.

### Test Structure

- `src/test/` — Unit tests (JUnit Jupiter + Google Truth). Model `toBaselineString()` tests, parser tests, diff tests.
- `src/gradleTest/` — Integration tests using GradleRunner. `AndroidProject` fixture creates temporary Android projects with the plugin injected via buildscript classpath (not `withPluginClasspath()`, to avoid AGP classloader isolation).

The `gradleTest` fixture reads `ANDROID_HOME` from environment, then falls back to `local.properties` up the directory tree.

## AGP Manifest Merger ActionType

BlameLogParser parses AGP's manifest merger blame log. AGP `Actions.ActionType` enum (6 values):

| ActionType | Handled | Description |
|---|---|---|
| `ADDED` | Yes | Element added to merged manifest |
| `INJECTED` | Yes | Injected by build system (uses-sdk, package) |
| `MERGED` | Yes | Merged with another manifest |
| `IMPLIED` | Yes | Implicitly added for lower targetSdk library |
| `CONVERTED` | Yes | Type conversion (nav-graph → intent-filter) |
| `REJECTED` | No | Rejected due to conflict (not in final manifest) |

If AGP adds new ActionType values, update regex patterns in `BlameLogParser`.

## Adding New Manifest Elements

Update these files in order:

1. `ManifestVisitor.kt` — Parse the new element (use `directChildElements()` for non-recursive, `getElementsByTagName()` for recursive)
2. `ManifestExtraction` — Add field
3. `models/` — Create model class implementing `ManifestEntry`
4. `ManifestShieldConfiguration.kt` — Add boolean flag (default `true` for important elements, `false` for rare ones)
5. `ShieldFlags.kt` — Add to interface + `applyConfig()`
6. `EnabledCategories.kt` — Add field + `from()` mapping
7. `ManifestShieldListTask.kt` — Add to section builder
8. `SourcesContentBuilder.kt` — Add to source-grouped output
9. Update unit tests and `README.md`

Reference:
- https://developer.android.com/guide/topics/manifest/manifest-element
- https://developer.android.com/guide/topics/manifest/application-element
