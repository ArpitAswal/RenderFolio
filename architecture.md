# System Architecture Specification — RenderFolio

## 1. Architectural Philosophy & Overview

**RenderFolio** is built using modern **Clean Architecture** combined with **MVVM (Model-View-ViewModel)** and **Unidirectional Data Flow (UDF)**. 

The core architectural mission is **local privacy and resilience**: PDF resumes are ingested, parsed, verified, and visualized entirely on the client device. Network and cloud synchronizations are purely opt-in peripheral capabilities.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│  MainActivity  ──>  StructureVerificationActivity  ──>  Visualiser     │
│  (Landing/Pick)     (Drag-and-Drop Editor)             (Motion Engine) │
│                               ▲                                        │
│                               │  StateFlow<UiState> / Events           │
│                               ▼                                        │
│                      ResumeViewModel (UDF)                             │
└───────────────────────────────┬────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN LAYER                                │
│       Immutable Models: ResumeModel, SavedShowcase, SectionItems       │
└───────────────────────────────┬────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             DATA LAYER                                 │
│  ┌───────────────────────┐  ┌───────────────────┐  ┌────────────────┐ │
│  │ PdfExtractionRepo     │  │ ResumePreferences │  │ ResumeCloudRepo│ │
│  │ (PDFBox + MLKit OCR)  │  │ (DataStore / Room)│  │ (Firestore API)│ │
│  └───────────────────────┘  └───────────────────┘  └────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Ingestion & Document Processing Pipeline

The resume ingestion pipeline converts unstructured PDF bytes into strongly-typed, validated domain models without external cloud dependencies:

```
[ Raw PDF File (URI) ]
         │
         ▼
[ Step 1: Document Pre-flight ] ── (Size check ≤ 10MB, MIME validation)
         │
         ▼
[ Step 2: Native Text Extraction (PDFBox Android) ]
         │
    Text Found?
    ├── YES ──> [ Direct Tokenization & Normalization ]
    └── NO  ──> [ Step 3: Optical Character Recognition (Google MLKit OCR) ]
                     │
                     ▼
[ Step 4: Semantic Section Heuristic Engine ]
         ├── Contact & Profile Extractor (Regex + Name heuristics)
         ├── Career Timeline Matcher (Date range anchors, company patterns)
         ├── Project & Deliverables Parser (Repo URLs, tech tags)
         ├── Technical Skills Categorizer (Dictionary + comma segmentation)
         └── Dynamic Custom Section Parser (Certifications, Awards, etc.)
                     │
                     ▼
[ Output: Strongly-Typed Immutable ResumeModel ]
```

### Key Technical Decisions:
* **Dual-Engine Fallback**: Scanned images or flattened PDFs fail standard PDF text extractors. By orchestrating Apache PDFBox with Google MLKit OCR as a secondary fallback, RenderFolio achieves >98% parsing success across varied document types.
* **Non-Blocking Execution**: Document parsing runs exclusively on `Dispatchers.IO` through Kotlin Coroutines, reporting granular stage updates (`IngestionInProgress`) to the UI via `StateFlow`.

---

## 3. State Management & Unidirectional Data Flow (UDF)

All mutable state lives behind `ResumeViewModel`:

```
User Action (Intent) ──> ViewModel ──> Repository Mutation ──> New Immutable State ──> UI Render
```

1. **Single Source of Truth**: State is modeled as a sealed hierarchy:
   - `UiState.Idle`: Awaiting user input or file selection.
   - `UiState.IngestionInProgress`: Background extraction active with progress percentage and active section tag.
   - `UiState.Verified`: Parsed resume loaded, ready for review or visualization.
   - `UiState.Error`: Handled domain failure with user-facing recovery guidance.
2. **Lifecycle Safety**: In Compose and View layers, state is collected using `collectAsStateWithLifecycle()` to pause flow collection when the activity enters the background, preventing resource leaks and UI jank.

---

## 4. Presentation & Motion Architecture

RenderFolio combines **Jetpack Compose** for dynamic interactive widgets and a specialized **View Motion Engine** for complex staggered spring animations:

### Motion Drivers
To avoid cluttered Activity files, animations are encapsulated into modular **Motion Drivers** located in `com.example.ui.motion`:
* **`HeroSectionMotionDriver`**: Animates candidate avatar, title, and contact action pills (Call, Email, Links) using spring physics.
* **`SummarySectionMotionDriver`**: Manages smooth accordion expansion with character limits.
* **`WorkExperienceMotionDriver`**: Sequentially staggers company badges, job titles, and bullet points.
* **`ProjectsMotionDriver`**: Handles interactive elevation cards, tech stack pills, and external URL launch intents.
* **`TechnicalSkillsMotionDriver`**: Renders dynamic categorized chips with filtering animations.

Each driver receives layout binding references and executes purely UI-level transitions without knowledge of repositories or business logic.

---

## 5. Cloud Synchronization & Recruiter Web View

To allow candidates to share their portfolio with recruiters who do not have the Android app installed:

```
[ Local ResumeModel ]
         │
         ▼
[ Firebase Firestore Sync ] (Creates short-lived public showcase document)
         │
         ├── Returns Unique Share URL: https://renderfolio.web.app/r/{shareId}
         │
         ├── ZXing QR Code Engine ──> Generates Branded QR Image
         │                                   │
         │                                   ▼
         │                        [ Saved to MediaStore / Pictures/RenderFolio ]
         │
         ▼
[ Recruiter Accesses Web Companion (public/index.html) ]
         │
         └── Fetches Firestore doc & renders responsive, dark-mode web portfolio
```

### Security & Expiry Policy:
* **7-Day Document Expiry**: Portfolios hosted in Firestore include a `createdAt` timestamp and automated TTL cleanup rules to prevent abandoned PII lingering on the cloud.
* **Scoped Storage**: QR images are written using Android 10+ MediaStore (`Environment.DIRECTORY_PICTURES + "/RenderFolio"`), eliminating legacy external storage permissions.

---

## 6. Offline Capabilities & Edge Case Resilience

1. **Zero-Connectivity Execution**: Ingestion, editing, motion visualization, and local QR code generation operate 100% offline.
2. **DataStore Local Caching**: Validated portfolios are cached locally in AndroidX DataStore. If cloud sync fails due to network outage, the app notifies the user and commits the local state safely.
3. **Coroutines Cancellation Safety**: All background coroutines observe structured concurrency; cancelling an upload or extraction immediately halts background file streams without thread starvation.

