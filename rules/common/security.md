# Common Application Security Rules

## 1. Secrets & Credentials
- **Zero Hardcoded Secrets**: Never commit API keys, OAuth secrets, private keys, signing keystores (`.jks`, `.keystore`, `.p12`), or tokens into source files or version control.
- Load secrets from untracked files (`.env`, `local.properties`) and verify they are listed in `.gitignore`.
- Treat client-side binaries as public. Never store production master keys inside mobile/web code.

## 2. Secure Local Storage
- Never store auth tokens, session tokens, or PII in plaintext storage (`SharedPreferences`, `UserDefaults`, `localStorage`, unencrypted SQLite).
- Use platform-backed encrypted storage (`flutter_secure_storage`, Android Keystore / EncryptedDataStore, iOS Keychain).

## 3. Safe Networking
- Enforce HTTPS/TLS 1.2+ for all network traffic.
- Never enable cleartext traffic in release builds.
- Redact `Authorization` headers, passwords, and tokens from all console and crash logs.
- Set explicit connect, read, and write timeouts on all network clients.

## 4. Input Validation & WebViews
- Validate and sanitize all external inputs: deep links, push notification payloads, and intent extras.
- Use parameterized queries for local databases (Drift, Sqflite, Room).
- Restrict WebViews: disable unnecessary JavaScript, disable file scheme access, and validate navigation URLs.