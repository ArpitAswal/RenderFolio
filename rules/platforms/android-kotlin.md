# Platform Extension: Native Android, Kotlin & Jetpack Compose

> **Activation**: Apply when working with `build.gradle.kts`, `AndroidManifest.xml`, or `*.kt` files.

## 1. Unidirectional Data Flow (UDF) & ViewModels
- **Single State Stream**: Expose a single immutable `StateFlow<UiState>` via `asStateFlow()` from the `ViewModel`.
- **Lifecycle-Aware Collection**: In Compose, collect UI state with `collectAsStateWithLifecycle()` from `androidx.lifecycle:lifecycle-runtime-compose` so upstream flows halt when the app is in the background.
- **Framework Separation**: Never pass `Context`, `Activity`, or `View` references into `ViewModel`s, repositories, or use cases.

## 2. Jetpack Compose Best Practices
- **State Hoisting**: Separate stateful screen composables (which handle dependency injection and collect flow state) from stateless, previewable content composables (which accept plain data and event callbacks).
- **Modifier Discipline**: Every reusable composable should accept an optional `modifier: Modifier = Modifier` as its first optional parameter and apply it to its root layout node.
- **List Stability**: Always pass a stable, unique `key` lambda inside `LazyColumn` / `LazyRow` `items(...)`. Ensure types passed into composables are stable or annotated with `@Immutable` / `@Stable`.
- **Side Effects**: Execute asynchronous tasks, snackbars, or timers strictly inside `LaunchedEffect`, `DisposableEffect`, or `SideEffect` blocks, never in the body of a composable.

## 3. Kotlin Coroutines & Threading
- **Structured Concurrency**: Never use `GlobalScope`. Always launch within `viewModelScope`, `lifecycleScope`, or an injected feature `CoroutineScope`.
- **Main Safety**: Repositories and data sources must be main-safe. Switch dispatchers using an injected `CoroutineDispatcher` via `withContext(ioDispatcher)`.
- **Preserve Cancellation**: When catching general `Exception` inside a coroutine, rethrow `CancellationException` (`if (e is CancellationException) throw e`) so structured cancellation works correctly.

## 4. Room Database & Local Persistence
- **DAOs**: Use Kotlin `Flow` for reactive queries and `suspend` functions for one-shot inserts/updates/deletes.
- **Type Converters**: Provide explicit `@TypeConverter` functions for complex data types (e.g. lists, embedded models).
- **Migrations**: Always define structured schema migrations or verify Room version updates without destroying user data.

## 5. Serialization & Network (Moshi / Retrofit)
- Use `@JsonClass(generateAdapter = true)` for Moshi-parsed models to avoid reflection overhead and leverage KSP code generation.
- Map network responses to domain models inside repository implementations; do not expose Moshi DTOs directly to the UI layer.

## 6. Verification Commands
- Compile Check: `./gradlew compileDebugKotlin`
- Lint: `./gradlew lintDebug`
- Unit Tests: `./gradlew testDebugUnitTest`
- Full Check: `./gradlew check`
