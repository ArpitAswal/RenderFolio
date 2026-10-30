---
description: Run the 5-gate pre-completion verification loop (diff audit, format, analyze, test, report)
---

1. Load `.agents/skills/verification-loop/SKILL.md`.
2. Run:
   - Gate 1: Git diff and secret check (`rules/common/security.md`)
   - Gate 2: Formatter / codegen check
   - Gate 3: Static analysis (`flutter analyze` or platform equivalent)
   - Gate 4: Automated tests (`flutter test` or platform equivalent)
   - Gate 5: Honest pass/fail summary