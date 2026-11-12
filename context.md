# Project Context (`context.md`)

## 1. Stack & Architecture
- **Application Name**: RenderFolio (rebranded from ResumeShowcase)
- **Platform**: Native Android (API 24 to 37)
- **Language**: Kotlin 2.4.x (Coroutines, Flow, Serialization)
- **UI Paradigm**: Jetpack Compose + Material 3 + View System Motion Drivers
- **State Management**: Lifecycle ViewModel + StateFlow (Unidirectional Data Flow)
- **Document Processing**: Apache PDFBox Android + Google MLKit Text Recognition
- **Persistence**: Room Database (KSP) + DataStore Preferences
- **Cloud & Sharing**: Firebase Firestore, Firebase Hosting, Firebase Auth, ZXing QR Code
- **Networking**: Retrofit 3.0 + OkHttp 5.5 + Moshi (KSP Codegen)

---

## 2. Key Architectural Decisions (ADRs)

| ADR ID | Decision | Rationale |
| :--- | :--- | :--- |
| **ADR-001** | **On-Device Parsing Pipeline** | Process PDF files locally using PDFBox Android + MLKit OCR fallback to ensure 100% user privacy and offline capability. |
| **ADR-002** | **Dedicated Motion Drivers** | Isolate View-level staggered animations into `*MotionDriver` classes to prevent bloated Activities and keep presentation concerns separated. |
| **ADR-003** | **Stateless Web Companion** | Host a lightweight static HTML5/CSS/JS viewer (`public/index.html`) on Firebase Hosting so recruiters can review portfolios without installing the Android app. |
| **ADR-004** | **Scoped Storage for QR Images** | Write generated QR codes to `MediaStore.Images.Media.RELATIVE_PATH` (`Pictures/RenderFolio`) to comply with Android 10+ without runtime storage permissions. |
| **ADR-005** | **Automated Cloud TTL Expiry** | Apply a 7-day automated document expiration on Cloud Firestore portfolios to prevent indefinite retention of candidate PII. |
| **ADR-006** | **Dual Deep Linking Schemes** | Support universal HTTPS links (`https://renderfolio.web.app/r/{id}`) and custom scheme (`renderfolio://r/{id}`) with legacy fallback. |

---

## 3. Active Conventions & Invariants
- **Immutability by Default**: All models (`ResumeModel`, `SavedShowcase`) are immutable Kotlin data classes.
- **Main Safety**: Repositories must switch to `Dispatchers.IO` for file I/O, parsing, or network calls.
- **Zero Hardcoded Secrets**: Secrets must reside in `local.properties` or environment variables; never commit API keys or keystores.
- **Pure Compose or Pure XML per Screen**: Never mix hybrid rendering paradigms within the same screen layout.

---

## 4. Primary Verification Commands
- **Compilation Check**: `./gradlew compileDebugKotlin`
- **Lint Check**: `./gradlew lintDebug`
- **Unit & Robolectric Tests**: `./gradlew testDebugUnitTest`

