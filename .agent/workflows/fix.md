---
description: Apply a direct, surgical code fix to resolve a known flaw without extra planning or questions
---

1. Load `rules/common/coding_style.md` and active platform rules (`rules/platforms/android-kotlin.md`, `rules/platforms/android-firebase.md`).
2. Receive the user's target file(s) and the exact flaw to be fixed.
3. Apply the minimal, surgical code changes needed to resolve the issue. Do not perform drive-by formatting, unprompted refactoring, or touch unrelated lines.
4. Run `./gradlew compileDebugKotlin` to verify that the project compiles with 0 errors.
5. Present a clear, concise summary showing the exact lines modified.

