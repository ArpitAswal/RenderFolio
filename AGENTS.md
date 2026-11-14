# AI Engineering Orchestration (`AGENTS.md`)

## 1. Core Engineering Principles
1. **Plan Before Code**: For any non-trivial feature, architectural change, or multi-file refactor, produce a concise implementation plan (`personas/planner.md`) covering affected modules, data models, interface contracts, and edge cases before mutating source files.
2. **Immutability by Default**: Prefer immutable data structures, value objects, and unidirectional data flow (`val` in Kotlin, immutable state data classes). Mutate state only behind explicit state holders (`ViewModel` + `StateFlow`).
3. **Separation of Concerns**: Keep domain and business logic independent of UI frameworks, navigation routers, and platform SDKs. Match modularity to domain complexity without unnecessary boilerplate.
4. **Minimal Diff Discipline**: Modify only the files and lines required for the task. Never perform drive-by formatting, unprompted dependency updates, or speculative abstractions.
5. **Search & Inspect First**: Read existing project conventions, dependency manifests, and adjacent modules before introducing new patterns or libraries.
6. **Dead File & Orphan Elimination**: Before completing verification or pushing code, scan for and remove obsolete or unreferenced tech-stack files (unused `*.kt` classes, unreferenced `*.xml` layouts or drawables, dead qualifier folders).

## 2. Standard Execution Workflow
1. **Understand & Scope**: Read relevant files and inspect `context.md` (if present).
2. **Plan**: Define step-by-step phases, interface boundaries, and test cases using `personas/planner.md`.
3. **Test-Driven Implementation (`RED → GREEN → REFACTOR`)**:
   - Write or update failing tests first (`skills/tdd-workflow/SKILL.md`).
   - Implement the minimal code required to pass.
   - Refactor for clarity, immutability, and adherence to `rules/common/coding_style.md`.
4. **Verify (`skills/verification-loop/SKILL.md`)**:
   - Run the platform's native static analyzer, formatter, and test suite on affected modules (`./gradlew testDebugUnitTest lintDebug`).
   - Review diffs against `personas/code-reviewer.md` and `rules/common/security.md`.

## 3. Personas (Roles)
- **Orchestrator**: `personas/orchestrator.md` (Master task intake, routing, and workflow chaining).
- **Analyzer**: `personas/analyzer.md` (Evaluates requirements, challenges assumptions, trade-offs with pros/cons).
- **Architect**: `personas/architect.md` (Enforces modular design, separation of concerns, and clean boundaries).
- **Planner**: `personas/planner.md` (Defines structured phased plans before any code changes).
- **Adaptive Screen Agent**: `personas/android-adaptive-screen-agent.md` (Designs responsive UI across phone, foldable, and tablet).
- **TDD Guide**: `personas/tdd-guide.md` (Coaches red-green-refactor test cycles).
- **Code Reviewer**: `personas/code-reviewer.md` (Audits code diffs, specific screens, or full projects for critical bugs, logic flaws, UI stretching, and edge cases).
- **Security Reviewer**: `personas/security-reviewer.md` (Audits manifests, storage, network, and secrets against `rules/common/security.md`).
- **Build Error Resolver**: `personas/build-error-resolver.md` (Surgically fixes build and lint failures).
- **Technical Writer**: `personas/technical-writer.md` (Generates framework-agnostic README, CHANGELOG, Architecture, and Context docs).

## 4. Baseline Standards
Always apply the shared baseline rules:
- `rules/common/coding_style.md`
- `rules/common/security.md`
- `rules/common/testing.md`
- `rules/common/development_workflow.md`
- `rules/common/adaptive-ui.md`

## 5. Platform Extensions for This Project (Native Android)
Load the platform rules matching this workspace:
- Android & Kotlin & Compose: `rules/platforms/android-kotlin.md`
- Android Adaptive UI: `rules/platforms/android-adaptive-ui.md`
- Android Firebase: `rules/platforms/android-firebase.md`
- Android Security Hardening: `rules/platforms/android-security.md`
