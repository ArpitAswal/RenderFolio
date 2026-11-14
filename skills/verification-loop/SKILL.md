---
name: verification-loop
description: Executes a 5-gate pre-completion verification check before marking tasks complete.
---

# Skill: Verification Loop

## The 5 Gates

### Gate 1 — Diff, Secrets, Architecture & Cleanliness Audit
- Inspect `git status` and `git diff`.
- Verify no debug logging statements (`Log.d`, `println`), unwanted temp files, or hardcoded secrets (`rules/common/security.md`).
- **Architectural Boundary Check**: Verify that UI presentation files (Activities, Adapters, Composables) do not directly import or call database DAOs or network APIs. Ensure MVVM separation of concerns is preserved.
- **Dead & Orphaned Files Audit**: Verify no unreferenced or obsolete tech-stack files (e.g. abandoned `*.kt` classes, unreferenced `*.xml` layouts or drawables, dead qualifier folders) remain in the workspace. Remove or flag them before completing verification.

### Gate 2 — Compilation & KSP Codegen
- Verify Kotlin compilation and Room / Moshi KSP code generation:
  `./gradlew compileDebugKotlin`

### Gate 3 — Static Analysis & Lint
- Run Android Lint to catch XML/Compose errors, missing permissions, and memory hazards:
  `./gradlew lintDebug`

### Gate 4 — Automated Unit Tests
- Execute all unit tests and Robolectric tests:
  `./gradlew testDebugUnitTest`

### Gate 5 — Handover Summary
- Output a clear pass/fail summary detailing:
  - Files modified or created
  - Verification commands executed with exit status
  - Any notes or follow-up recommendations
