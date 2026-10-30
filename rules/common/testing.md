# Common Testing Strategy & Standards

## 1. Testing Philosophy & Coverage Target
- Test **observable behavior and domain contracts**, not private implementation details.
- **Coverage Target**: 80% line/branch coverage baseline for domain logic, state holders, repositories, and data mappers.
- High target (95–100%) for financial calculations, authentication, and offline sync.
- Pragmatic exemptions for thin UI wrappers, generated code, and dependency injection wiring.

## 2. Test Pyramid
1. **Unit Tests (~70%)**: Test use cases, state management (BLoC/Cubit, Riverpod, ViewModel, custom hooks), serializers, and validators. Must run in milliseconds with mock/fake dependencies.
2. **Widget / Component Tests (~20%)**: Test UI rendering across loading, empty, error, and populated states plus user taps.
3. **Integration Tests (~10%)**: Test critical end-to-end user journeys (login, checkout, offline-to-online sync).

## 3. Determinism
- Zero arbitrary `sleep` or wall-clock delays in tests.
- Use virtual time and test dispatchers (`fakeAsync` in Dart, `runTest` in Kotlin Coroutines, fake timers in Vitest).
- Follow **Arrange → Act → Assert** (Given / When / Then).