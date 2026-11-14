---
description: Run a comprehensive pre-publish security audit and Git hygiene check before pushing to a public repository
---

1. Load `personas/security-reviewer.md`, `rules/common/security.md`, and platform security rules (`rules/platforms/android-security.md`, `rules/platforms/android-firebase.md`).
2. Inspect Git tracked files (`git ls-files`) to verify no keystores (`*.jks`), credentials (`google-services.json`, `local.properties`), or personal resume PDFs (`docs/*.pdf`) are actively tracked in Git history.
3. Scan source code, build scripts (`build.gradle.kts`), and manifests (`AndroidManifest.xml`) for:
   - Hardcoded API keys, secrets, or passwords.
   - Insecure exported components (`android:exported="true"` without permission).
   - Cleartext traffic or insecure network configurations.
4. Audit `.gitignore` coverage for sensitive files, credentials, and OS junk (`.DS_Store`). If a sensitive file is currently tracked, specify the required `git rm --cached` command.
5. **Orphaned & Dead Code Elimination**:
   - Scan for abandoned, unused, or obsolete tech-stack files (e.g. unreferenced `*.kt` classes, obsolete `*.xml` layouts or drawables, dead qualifier directories like old `layout-w600dp`).
   - Remove or list them for safe deletion before pushing to keep the repository lean and clean.
6. Review Markdown files:
   - Keep public documentation (`README.md`, `CONTRIBUTING.md`) tracked.
   - List internal agent instructions (`.agent/`, `personas/`, `skills/`, `.codex/`) and ask the user whether these should remain tracked or be added to `.gitignore`.
6. Output the structured 4-part **Public Readiness Report** defined in `personas/security-reviewer.md`.
7. Pause and wait for user confirmation before modifying `.gitignore` or executing any Git cleanup commands.
