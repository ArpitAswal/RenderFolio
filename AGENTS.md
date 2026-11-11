# AI Engineering Orchestration (`AGENTS.md`)

## 1. Core Engineering Principles
1. **Plan Before Code**: For any non-trivial feature, architectural change, or multi-file refactor, produce a concise implementation plan (`personas/planner.md`) covering affected modules, data models, interface contracts, and edge cases before mutating source files.
2. **Immutability by Default**: Prefer immutable data structures, value objects, and unidirectional data flow (`final`/`const` in Dart, `val` in Kotlin, `let` in Swift, `const`/readonly in React). Mutate state only behind explicit state holders.
3. **Separation of Concerns**: Keep domain and business logic independent of UI frameworks, navigation routers, and platform SDKs. Match modularity to domain complexity without unnecessary boilerplate.
4. **Minimal Diff Discipline**: Modify only the files and lines required for the task. Never perform drive-by formatting, unprompted dependency updates, or speculative abstractions.
5. **Search & Inspect First**: Read existing project conventions, dependency manifests, and adjacent modules before introducing new patterns or libraries.

## 2. Standard Execution Workflow
1. **Understand & Scope**: Read relevant files and inspect `context.md` (if present).
2. **Plan**: Define step-by-step phases, interface boundaries, and test cases using `personas/planner.md`.
3. **Test-Driven Implementation (`RED → GREEN → REFACTOR`)**:
   - Write or update failing tests first (`skills/tdd-workflow/SKILL.md`).
   - Implement the minimal code required to pass.
   - Refactor for clarity, immutability, and adherence to `rules/common/coding_style.md`.
4. **Verify (`skills/verification-loop/SKILL.md`)**:
   - Run the platform's native static analyzer, formatter, and test suite on affected modules.
   - Review diffs against `personas/code-reviewer.md` and `rules/common/security.md`.

## 3. Personas (Roles)
- **Analyzer**: `personas/analyzer.md` (Evaluates requirements, challenges assumptions, and outputs architectural trade-offs with pros/cons before code is written).
- **Orchestrator**: `personas/orchestrator.md` (Routes the task and chains workflows).

## 4. Baseline Standards
Always apply the shared baseline rules:
- `rules/common/coding_style.md`
- `rules/common/security.md`
- `rules/common/testing.md`
- `rules/common/development_workflow.md`

## 5. Conditional Platform Extensions
Load ONLY the platform rule matching your active workspace files:
- Flutter/Dart: `rules/platforms/flutter-dart.md`
- Android/Kotlin: `rules/platforms/android-kotlin.md`
- iOS/Swift: `rules/platforms/ios-swift.md`
- React/TypeScript: `rules/platforms/react-web.md`
- Flutter Adaptive UI: `rules/platforms/flutter-adaptive-ui.md`