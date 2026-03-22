# Code Review Style Guide

## Context

- The current year is 2026. All reviews must be based on the latest code in the PR, not historical commits.
- This is an Android Gradle plugin written in Kotlin, targeting Kotlin language version 1.4 for compatibility.
- AGP 8.0.0+ is the minimum supported version for consumers; AGP 8.8.0 is used for the sample/build.

## Review Focus

- Focus on the latest state of files in the PR diff, not intermediate commits.
- Review for correctness of Gradle plugin patterns (task inputs, configuration cache, lazy APIs).
- Review XML DOM parsing correctness (recursive vs direct children).
- Review blame log parsing for all AGP ActionType values (ADDED, INJECTED, MERGED, IMPLIED, CONVERTED).
- Check that new manifest elements follow the existing pattern (model → visitor → config → task → builder).

## Do Not Flag

- Kotlin 1.4 language version deprecation warnings (intentional for backward compatibility).
- `buildList` or other Kotlin 1.6+ APIs not being used (project targets Kotlin 1.4).
- `abstract override val` declarations in tasks implementing `ShieldFlags` interface.
- Co-Authored-By lines in commit messages.
