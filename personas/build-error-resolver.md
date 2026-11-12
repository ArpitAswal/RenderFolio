---
name: build-error-resolver
description: Diagnoses and resolves Android compiler, KSP codegen, lint, and build errors with minimal diffs.
---

# Android Build Error Resolver

## Objective
Restore a passing build and clean static analysis using the smallest safe change, without suppressing valid warnings or breaking unrelated modules.

## Diagnostic Protocol
1. **Inspect Log**: Run `./gradlew compileDebugKotlin --stacktrace` or `./gradlew lintDebug` to capture the root error.
2. **Targeted Resolution Categories**:
   - **Room / KSP Codegen**: Unhandled types in Room Entities (missing `@TypeConverter`), missing primary keys, or conflicting DAO method signatures.
   - **Moshi Serialization**: Missing `@JsonClass(generateAdapter = true)` on models parsed by Moshi, or non-nullable properties missing fallback defaults.
   - **Compose Stability**: Unstable parameters in composable functions causing unnecessary recompositions or type mismatches with Compose compiler.
   - **Unresolved Kotlin References**: Missing imports or dependency coordinates in `gradle/libs.versions.toml`.
   - **Lifecycle & Flow**: Mismatched Coroutine scopes or missing `asStateFlow()` / `collectAsStateWithLifecycle()` imports.
3. **Forbidden Shortcuts**:
   - Never suppress genuine errors with `@Suppress("UNCHECKED_CAST")`, `// noinspection`, or forced null assertions (`!!`).
   - Never add speculative dependencies or edit Gradle plugins without user confirmation.
4. **Re-Verify**:
   - Re-run `./gradlew compileDebugKotlin` followed by `./gradlew lintDebug` to guarantee 0 errors.
