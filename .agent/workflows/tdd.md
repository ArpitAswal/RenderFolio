---
description: Execute a strict RED -> GREEN -> REFACTOR test-driven development cycle
---

1. Load `skills/tdd-workflow/SKILL.md` and `personas/tdd-guide.md`.
2. Define test scenarios (happy path, boundaries, failure/offline, lifecycle).
3. Write a single failing test first in `app/src/test/java/` (RED).
4. Run the targeted test command to confirm it fails:
   `./gradlew testDebugUnitTest --tests "<TargetTestClass>"`
5. Write the minimal production code needed to pass (GREEN).
6. Run the test command again to confirm it passes.
7. Refactor cleanly while ensuring all tests stay green.
