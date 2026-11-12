---
description: Perform a quality, concurrency, lifecycle, and security code review
---

1. Load `personas/code-reviewer.md`, `rules/common/coding_style.md`, and `rules/common/security.md`.
2. Load the corresponding platform rules from `rules/platforms/` (`android-kotlin.md`, `android-adaptive-ui.md`, `android-firebase.md`).
3. Review the current git diff or specified files.
4. Output findings grouped by `[CRITICAL]`, `[HIGH]`, `[MEDIUM]`, and `[LOW / NIT]` with exact `file_path:line_number` and corrective code snippets.
