# Platform Extension: Android Security & Hardening Rules

> **Activation**: Apply when reviewing or auditing Android manifests, Gradle build scripts, storage, or Android components before publishing.

## 1. Android Manifest Security
- **Exported Components**: Explicitly declare `android:exported="false"` on all `<activity>`, `<service>`, `<receiver>`, and `<provider>` tags unless specifically intended as external entry points (such as the main launcher activity).
- **Cleartext Traffic**: Never enable `android:usesCleartextTraffic="true"`. All network traffic must use secure HTTPS endpoints.
- **Deep Links**: Ensure deep links validate incoming URIs and sanitize all parameters before passing them to internal screens or state holders.

## 2. Keystores & Signing Configurations
- **No Hardcoded Passwords**: Never hardcode keystore passwords, key passwords, or store paths in `build.gradle.kts` or `build.gradle`.
- **Safe Properties Injection**: Load signing credentials dynamically from `local.properties` or CI environment variables.
- **Ignore Keystores**: Ensure all certificate files (`*.jks`, `*.keystore`, `*.p12`, `*.pem`) are strictly excluded in `.gitignore`.

## 3. Storage & File Sharing
- **Secure Storage**: Store user credentials, session tokens, and sensitive preferences using Android DataStore or `EncryptedSharedPreferences`, never in raw, unencrypted SharedPreferences.
- **FileProvider Security**: Configure `FileProvider` with minimal filesystem access paths in `res/xml/file_paths.xml`.
- **URI Permission Grants**: Grant temporary, read-only permissions via `Intent.FLAG_GRANT_READ_URI_PERMISSION` when sharing local files (such as generated PDFs) with external apps.

## 4. Local Database & Resume PII
- **Data Protection**: Ensure user resumes, contact information, and parsed profiles stored in Room database remain sandboxed within private app storage.
- **Sample/Test PDFs**: Ensure sample resumes or test PDFs containing real applicant contact details in `docs/` are excluded from public source control.

## 5. Build Configuration & Logging
- **Release Hygiene**: Strip or disable `Log.d` / `Log.v` statements that output raw resume content or user data in release builds.
- **ProGuard / R8**: Maintain up-to-date `proguard-rules.pro` to obfuscate sensitive domain logic and remove diagnostic logging in production APKs/bundles.

