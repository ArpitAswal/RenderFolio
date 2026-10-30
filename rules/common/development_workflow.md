# Common Development & Git Workflow Rules

## 1. Execution Lifecycle
1. **Context Check**: Check `context.md` (if present) and recent git commits to understand active architectural direction.
2. **Impact Analysis**: Search call sites across the codebase before altering shared models or API contracts.
3. **Incremental Changes**: Build features in verifiable layers (Contracts → Data Layer + Tests → State Logic + Tests → UI).
4. **Verification Gate**: Run formatter, static analysis, and unit tests before completing any task.

## 2. Git Hygiene
- Make atomic, single-purpose commits.
- Follow Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- Never commit build artifacts (`build/`, `.dart_tool/`, `.gradle/`, `node_modules/`).
- Never run destructive commands (`git reset --hard`, `git push --force`) without confirmation.