---
name: verification-loop
description: Executes a 5-gate pre-completion verification check before marking tasks complete.
---

# Skill: Verification Loop

## The 5 Gates
1. **Gate 1 — Diff Audit**: Inspect `git status` and `git diff`. Verify no debug logs, unintended changes, or committed secrets.
2. **Gate 2 — Formatting & Codegen**: Run formatters and verify generated code is up to date.
3. **Gate 3 — Static Analysis**: Run the platform analyzer/typechecker.
4. **Gate 4 — Automated Tests**: Run unit and component test suites.
5. **Gate 5 — Summary**: Report exact pass/fail results.