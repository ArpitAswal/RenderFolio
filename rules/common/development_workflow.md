# Common Development & Git Workflow Rules

## 1. Execution Lifecycle
1. **Context Check**: Check `context.md` (if present) and recent git commits to understand active architectural direction.
2. **Impact Analysis**: Search call sites across the codebase before altering shared models or API contracts.
3. **Incremental Changes**: Build features in verifiable layers (Contracts → Data Layer + Tests → State Logic + Tests → UI).
4. **Verification Gate**: Run compiler, static analysis, and unit tests before completing any task (`./gradlew compileDebugKotlin lintDebug testDebugUnitTest`).

## 2. Git Hygiene & Cleanliness
- Make atomic, single-purpose commits.
- Follow Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- Never commit build artifacts (`build/`, `.dart_tool/`, `.gradle/`, `node_modules/`).
- **Dead & Orphaned File Cleanup**: Before committing or pushing, scan for and eliminate orphaned tech-stack files (e.g., unused `*.kt` classes, unreferenced `*.xml` layouts or drawables, dead qualifier folders) so the repository remains lean and clutter-free.
- Never run destructive commands (`git reset --hard`, `git push --force`) without confirmation.