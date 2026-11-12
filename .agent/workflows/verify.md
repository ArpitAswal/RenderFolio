---
description: Run the 5-gate pre-completion verification loop (diff audit, format, analyze, test, report)
---

1. Load `skills/verification-loop/SKILL.md`.
2. Run:
   - Gate 1: Git diff and secret check (`rules/common/security.md`)
   - Gate 2: Formatter and KSP codegen check (`./gradlew compileDebugKotlin`)
   - Gate 3: Static analysis and linting (`./gradlew lintDebug`)
   - Gate 4: Automated tests (`./gradlew testDebugUnitTest`)
   - Gate 5: Honest pass/fail summary
