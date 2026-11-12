---
name: verification-loop
description: Executes a 5-gate pre-completion verification check before marking tasks complete.
---

# Skill: Verification Loop

## The 5 Gates

### Gate 1 — Diff & Secrets Audit
- Inspect `git status` and `git diff`.
- Verify no debug logging statements (`Log.d`, `println`), unwanted temp files, or hardcoded secrets (`rules/common/security.md`).

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
