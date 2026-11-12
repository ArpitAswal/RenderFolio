# Universal Security & Data Protection Rules

## 1. Secrets & Credentials
- **Zero Secrets in Code**: Never hardcode API keys, service account JSONs, bearer tokens, passwords, or private keys in any source code, configuration, or Git commit history.
- **Environment & Local Configuration**: Store sensitive credentials in local environment variables or untracked configuration files (e.g., `local.properties`, `.env`) that are strictly excluded via `.gitignore`.
- **Public Key Scanning**: Before committing, scan diffs for common secret patterns (Google API keys starting with `AIzaSy`, AWS access keys, GitHub tokens, private keys `BEGIN PRIVATE KEY`).

## 2. Git Hygiene & Tracked Files
- **Verify Tracked Files**: Adding a file to `.gitignore` does not untrack files already stored in Git history. Always verify with `git ls-files` to confirm sensitive files are not tracked.
- **Untrack Staged Secrets**: If a sensitive file is accidentally tracked, remove it from the index immediately using `git rm --cached <file>` before committing.
- **Never Commit Test Credentials**: Never commit test accounts, mock API keys, or production database connection strings to public repositories.

## 3. Data Privacy & Logging
- **PII Protection**: Personally Identifiable Information (real user names, email addresses, phone numbers, resumes, or financial details) must never be committed to public repositories.
- **Logging Hygiene**: Remove or disable verbose debug logs (`println`, debug log statements) that print sensitive user data or authentication tokens in release builds.

## 4. Agent Instructions & Workspace Notes
- **Distinguish Documentation from Internal Notes**: Public project documentation (`README.md`, `CONTRIBUTING.md`, `docs/`) is intended for Git tracking.
- **Protect Private AI Instructions**: If internal agent workflows, system prompts, or scratchpad markdown files contain proprietary logic or private workflows, ensure they are explicitly added to `.gitignore`.
