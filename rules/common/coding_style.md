# Common Coding Style & Maintainability Rules

## 1. Immutability & Data Modeling
- Default all variables, properties, and collections to immutable references (`final`/`const` in Dart, `val` in Kotlin, `let` in Swift, `const` in TypeScript).
- Represent state transitions by returning new copies (e.g., `copyWith` in Dart, `copy()` in Kotlin data classes) rather than mutating shared objects in place.
- Model finite states explicitly using algebraic data types (Dart 3 / Kotlin sealed classes, Swift enums, TS discriminated unions) instead of multiple boolean flags (`isLoading`, `isError`).

## 2. Naming Conventions
- Types/Classes/Widgets/Components: `PascalCase`
- Functions/Methods/Variables: `camelCase`
- Constants: `camelCase` (Dart/Swift) or `UPPER_SNAKE_CASE` (Kotlin `const val` / TS)
- Files: `snake_case.dart` (Dart), `PascalCase.kt` (Kotlin), `PascalCase.swift` (Swift), `kebab-case.ts` (TS).

## 3. Function & File Size Guidelines
- **Functions**: Target **≤ 40–50 lines**, single level of abstraction.
- **Files**: Target **≤ 300–400 lines**.
- **Documented Exceptions**:
  1. Declarative UI trees (Flutter `build`, Compose `@Composable`, SwiftUI `body`, React JSX) when extracting sub-widgets reduces readability.
  2. Exhaustive pattern-matching `switch`/`when` statements on sealed types.
  3. Generated code (`*.g.dart`, `*.freezed.dart`, KSP/Room outputs).

## 4. Control Flow & Error Handling
- Use guard clauses and early returns; keep indentation ≤ 3 levels.
- Never swallow exceptions silently (`catch (_) {}`). Surface a typed domain failure or rethrow.