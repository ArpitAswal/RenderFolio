# Platform Extension: Android Firebase Integration

> **Activation**: Apply when working with Firebase modules (Firestore, Firebase Auth, Firebase AppCheck, Firebase AI).

## 1. Firebase Authentication
- Keep authentication state observation centralized inside repository classes.
- Expose the current user as a Kotlin `StateFlow<User?>` or sealed `AuthState` to prevent inconsistent auth state across screens.
- Handle session expiration and null user tokens gracefully before triggering cloud operations.

## 2. Cloud Firestore Rules & Operations
- **Typed Data Mapping**: Always map raw Firestore document snapshots into typed, immutable data models before exposing them to ViewModels.
- **Offline Persistence**: Rely on Firestore local cache for read resilience; avoid duplicate in-memory caching that can desync with the cloud.
- **Coroutines & Await**: Use `kotlinx-coroutines-play-services` (`.await()`) rather than callback listeners (`addOnSuccessListener`) for clean linear coroutine execution in repositories.

## 3. Firebase AppCheck & Security
- Enable Firebase AppCheck with Play Integrity for production releases to guard against unauthorized API abuse.
- Provide a debug AppCheck provider during local testing and CI to avoid blocking test runs.
- Validate that all Firestore security rules mirror the client-side authorization expectations.

