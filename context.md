# Project Context (`context.md`)

## 1. Stack & Architecture
- **Application**: RenderFolio (Android native resume showcase & visualizer)
- **Version**: `1.0` (`versionCode = 1`) | Min SDK: 24 | Target/Compile SDK: 37
- **Language**: Kotlin 2.4.x (Coroutines, StateFlow, Serialization)
- **UI Engine**: Jetpack Compose (Material 3) + View System Motion Drivers
- **Document Engine**: Apache PDFBox Android (local text) + Google ML Kit OCR (scanned fallback)
- **Persistence**: Room Database (KSP) + DataStore Preferences
- **Cloud & Share**: Firebase Firestore (7-day TTL), Firebase Hosting (`renderfolio.web.app`), ZXing QR Code
- **Networking**: Retrofit 3.0 + OkHttp 5.5 + Moshi (KSP Codegen)

---

## 2. Key Architectural Decisions (ADRs)

| ADR ID | Decision | Rationale |
| :--- | :--- | :--- |
| **ADR-001** | **On-Device Parsing Pipeline** | PDFBox Android + ML Kit OCR fallback ensures 100% privacy; raw resumes are never sent to external AI cloud APIs. |
| **ADR-002** | **Dedicated Motion Drivers** | Staggered physics animations live in separate `*MotionDriver` classes to prevent bloated Activities. |
| **ADR-003** | **Stateless Web Companion** | Hosted at `renderfolio.web.app` (`public/index.html`) so recruiters can view portfolios on any browser. |
| **ADR-004** | **Scoped Storage for QR Codes** | Saves QR images to `Pictures/RenderFolio` via MediaStore Scoped Storage without requiring runtime permissions. |
| **ADR-005** | **Automated Cloud TTL Expiry** | 7-day automatic Firestore document expiration protects candidate PII from indefinite storage. |
| **ADR-006** | **Dual Deep Linking Schemes** | Supports universal HTTPS (`https://renderfolio.web.app/r/{id}`) and custom scheme (`renderfolio://r/{id}`). |
| **ADR-007** | **3-Tier Adaptive UI System** | Phone (`layout/`), Tablet Portrait (`layout-sw600dp/` max-width 600dp), and Tablet Landscape (`layout-sw600dp-land/` dual-pane). |

---

## 3. Active Conventions & Invariants
- **Immutability by Default**: Models (`ResumeProfile`, `SavedShowcase`) are immutable Kotlin data classes.
- **Main Safety**: Repositories must switch to `Dispatchers.IO` for file I/O, parsing, or network calls.
- **Zero Hardcoded Secrets**: Secrets live in `.env` / `local.properties`; never commit keystores or tokens.
- **ViewBinding ID Parity**: All view IDs accessed in Kotlin code must exist across all layout qualifiers (`layout/`, `layout-sw600dp/`).
- **Rotation Safety**: Activities declare `configChanges` in the manifest to prevent state wipeout on device rotation.

---

## 4. Current Milestone & Verification
- **Current Milestone**: Production Release `v1.0.0` (Ready for Code Review & Final Verification).
- **Compilation Check**: `./gradlew compileDebugKotlin`
- **Lint Check**: `./gradlew lintDebug`
- **Unit & Robolectric Tests**: `./gradlew testDebugUnitTest`
