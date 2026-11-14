# System Architecture Specification — RenderFolio

> A comprehensive architectural guide explaining the **system design, data flow, and engineering decisions behind the user interface**, designed for developers, architects, and technical interview reviews.

---

## 1. Executive Architecture Pitch & Layer Boundaries

**RenderFolio** is built on modern **Clean Architecture** with **MVVM (Model-View-ViewModel)** and **Unidirectional Data Flow (UDF)**. 

### Core Architectural Philosophy:
1. **100% On-Device Privacy**: PDF parsing occurs strictly on the user's phone using local engines. Raw documents are never transmitted to cloud LLMs or third-party servers.
2. **Offline-First Resilience**: Full portfolio creation, dynamic section resequencing, physics-driven animation, and QR generation operate without an active internet connection.
3. **Ephemeral Cloud Sharing**: Cloud persistence is strictly opt-in, featuring automated 7-day document expiration to protect candidate personal data.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│                                                                        │
│   Screen 1: MainActivity      Screen 2: Verification     Screen 3: Visualiser
│   (Upload Dropzone/Compose)   (Drag-and-Drop Editor)     (6 Motion Drivers)  │
│              ▲                           ▲                        ▲    │
│              └───────────────────────────┼────────────────────────┘    │
│                                          │ StateFlow<UiState> / Events │
│                                          ▼                             │
│                               ResumeViewModel (UDF)                    │
└──────────────────────────────────────────┬─────────────────────────────┘
                                           │
                                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN LAYER                               │
│           Immutable Models: ResumeProfile, SavedShowcase, SectionItems │
└──────────────────────────────────────────┬─────────────────────────────┘
                                           │
                                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                │
│   ┌───────────────────────────┐ ┌───────────────────┐ ┌──────────────┐ │
│   │ PdfExtractionRepository   │ │ PreferencesRepo   │ │ CloudRepo    │ │
│   │ (PDFBox + ML Kit OCR)     │ │ (DataStore / Room)│ │ (Firestore)  │ │
│   └───────────────────────────┘ └───────────────────┘ └──────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Screen-by-Screen User Journey & Data Movement

To understand the system design, follow how data moves across the **three primary screens**:

```
[ Raw PDF Resume (URI) ]
           │
           ▼
┌──────────────────────────────────────┐
│ SCREEN 1: MainActivity (Ingestion)   │ ──> Validates size (≤10MB) & MIME type
└──────────────────┬───────────────────┘ ──> Runs local dual-engine parser on Dispatchers.IO
                   │ Emits unverified ResumeProfile
                   ▼
┌──────────────────────────────────────┐
│ SCREEN 2: StructureVerification      │ ──> Binds to ItemTouchHelper RecyclerView
└──────────────────┬───────────────────┘ ──> Allows inline editing & custom section creation
                   │ User taps "Compile & Save" ➔ Writes to DataStore / Room
                   ▼
┌──────────────────────────────────────┐
│ SCREEN 3: ResumeVisualiserActivity   │ ──> Staggers 6 Spring Motion Drivers
└──────────────────┬───────────────────┘ ──> Generates offline ZXing QR Code to Scoped Storage
                   │ (Opt-in "Share Online")
                   ▼
┌──────────────────────────────────────┐
│ WEB COMPANION: public/index.html     │ ──> Recruiter views responsive dark-mode portfolio
└──────────────────────────────────────┘     at https://renderfolio.web.app/r/{shareId}
```

---

## 3. The 4 Core Subsystems (Deep Dive)

### Subsystem A: Document Ingestion & Dual-Engine Parser
- **Dual-Engine Orchestration**:
  1. *Primary*: **Apache PDFBox Android** extracts native text streams and vector glyphs in under 800ms.
  2. *Fallback*: If native text is absent (e.g. scanned image PDF or flattened export), the pipeline automatically dispatches **Google ML Kit Text Recognition** to extract text via on-device machine learning.
- **Regex & Heuristic Normalization**:
  - Contact details (email, phone, LinkedIn, GitHub) are extracted via deterministic regex patterns.
  - Section headers ("Work Experience", "Education", "Projects") are anchored through fuzzy heading matching and segmented into strongly-typed domain lists.
- **Threading Model**: Entire pipeline runs on `Dispatchers.IO` using Kotlin Coroutines, emitting live progress states (`0% ➔ 100%`) through `StateFlow` to drive the UI loading animation.

### Subsystem B: Reactive State & Offline Persistence
- **Unidirectional Data Flow (UDF)**:
  - User interactions fire explicit intents into `ResumeViewModel`.
  - State is modeled as an immutable sealed hierarchy: `UiState.Idle`, `UiState.IngestionInProgress`, `UiState.Success`, `UiState.Error`.
- **Local Persistence**:
  - Ingestion drafts and active portfolios are cached locally in **AndroidX DataStore** and **Room Database (KSP)**.
  - If a network error occurs during cloud sharing, local data is completely safe, ensuring zero data loss.

### Subsystem C: Structure Verification, Dynamic Sections & Drag-and-Drop Resequencing
- **The 6 Core Mandatory Sections**:
  The structure verification pipeline enforces 6 foundation sections required to build the full visual portfolio:
  1. `section_personal` (Hero / Personal Info): Name, headline, location, phone, email, and social links (LinkedIn, GitHub).
  2. `section_summary` (Professional Summary): Core narrative, career background, and domain specialties.
  3. `section_experience` (Work Experience): Chronological job roles, responsibilities, bullet highlights, and client applications.
  4. `section_projects` (Featured Projects): Titles, descriptions, tech stack chips, demo URLs, and GitHub repository links.
  5. `section_skills` (Technical Skills): Categorized skill matrices (Languages, Frameworks, Cloud, DevOps, Tools).
  6. `section_education` (Education & Academics): Degrees, universities/boards, graduation years, and GPA/CGPA.
  *Why mandatory*: These 6 sections provide the essential data contracts required by the 6 downstream visualizer motion drivers.
- **Dynamic Custom Sections**:
  - Candidates can tap **"Add Custom Section"** via `AddCustomSectionBottomSheet` to append custom sections (e.g. *Certifications*, *Open Source Contributions*, *Publications*, *Volunteer Work*, *Awards*).
  - Categorized under `CustomSectionArchetype`, custom sections are instantiated as `custom_<title>` entries within `ResumeProfile.dynamicSections` and dynamically registered in `sectionOrder`.
- **Drag-and-Drop Resequencing Engine**:
  - Integrated via `ItemTouchHelper` using an `ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)`.
  - Touching a section's drag handle initiates `itemTouchHelper.startDrag(holder)`.
  - When dragged, `StructureSectionsAdapter.onItemMove(from, to)` physically swaps items in the adapter and updates `profile.sectionOrder`.
  - On Tablet Landscape (`sw600dp-land`), dual independent adapters manage the Left Pane (Personal, Summary, Skills, Education) and Right Pane (Experience, Projects, Custom) with dedicated touch helpers.
- **Visualizer Dynamic Rebuilding**:
  - Upon tapping **"Compile"**, the resulting `ResumeProfile` carries the candidate's exact `sectionOrder`.
  - `ResumeVisualiserActivity` dynamically reads `effectiveOrder`, looks up view containers via `viewMap`, and attaches each section card in the user's custom sequence with consistent 32dp vertical spacing.

### Subsystem D: Motion Physics & 3-Tier Adaptive UI Engine
- **Decoupled Motion Drivers**:
  - Rather than bloating Activities with animation logic, animations are isolated into dedicated drivers in `com.example.ui.motion`:
    - `HeroSectionMotionDriver`: Spring physics avatar and contact pill reveal.
    - `WorkExperienceMotionDriver`: Chronological timeline stagger.
    - `ProjectsMotionDriver`: Interactive card tilt and repo link launch.
    - `TechnicalSkillsMotionDriver`: Categorized skill pill animations.
- **3-Tier Adaptive Viewport System**:
  - **Phone (Compact)**: Single-column vertical stream wrapped in `NestedScrollView` (`fillViewport="true"`).
  - **Tablet Portrait (`sw600dp`)**: Single-column layout centered with `app:layout_constraintWidth_max="600dp"` to preserve optimal reading line lengths (45–75 characters per line).
  - **Tablet Landscape (`sw600dp-land`)**: Spacious two-column split (Summary & skills on left, experience & projects on right).
  - **Rotation Safety**: Activities declare `configChanges` in the manifest so device rotation preserves user form edits without Activity recreation.

### Subsystem E: Ephemeral Cloud Sharing & Web Companion
- **Cloud Firestore Synchronization**:
  - When the candidate chooses to share their portfolio, the payload is serialized and written to Cloud Firestore under a unique random document ID.
- **7-Day Automated Expiration (TTL)**:
  - Every shared document includes a `createdAt` timestamp. Automated Firestore rules reject reads on documents older than 7 days, preventing candidate PII from lingering indefinitely.
- **Offline QR Code Generation**:
  - Built-in **ZXing** engine generates high-contrast QR codes and saves them directly to `Pictures/RenderFolio` via Android 10+ MediaStore Scoped Storage without requiring runtime permissions.
- **Stateless Web Companion**:
  - Recruiters open `https://renderfolio.web.app/r/{shareId}` (`public/index.html`) on any desktop or mobile browser. It renders a responsive dark-mode portfolio matching the Android visualizer.

---

## 4. Key Architectural Trade-Offs (The "Why")

| Decision | Chosen Approach | Alternative Considered | Why This Won |
| :--- | :--- | :--- | :--- |
| **Parsing Engine** | **On-Device PDFBox + ML Kit** | Cloud LLM Parsing (Gemini / OpenAI API) | Zero API costs, zero network latency, and 100% user data privacy. Raw resumes never leave the device. |
| **Animation Logic** | **Modular Motion Drivers** | In-Activity View Animations | Keeps Activities under 300 lines, enforces Single Responsibility, and makes animations independently testable. |
| **QR Code Storage** | **MediaStore Scoped Storage** | Legacy External Storage (`WRITE_EXTERNAL_STORAGE`) | Zero runtime permission dialogs; fully compliant with Android 10, 11, 12, 13, and 14+ storage sandboxes. |
| **Cloud Lifecycle** | **7-Day Ephemeral TTL** | Permanent Database Records | Protects candidate privacy; prevents outdated resumes and candidate contact info from floating on the internet forever. |

---

## 5. Interview & System Design Explanation Cheat-Sheet

If asked to explain RenderFolio's system design in an interview, summarize it in these 4 points:

1. **"It's a Client-First Pipeline"**: We ingest PDF resumes locally via a dual-engine parser (PDFBox for native text, ML Kit OCR for scans) so candidates' personal resumes are never exposed to cloud servers.
2. **"Strict State Separation"**: We use MVVM with Unidirectional Data Flow. The UI emits user events, the ViewModel runs background parsing on `Dispatchers.IO`, and updates the screen through immutable `StateFlow` states.
3. **"Decoupled Presentation Engine"**: Animations are extracted into standalone Motion Drivers to prevent bloated Activities, and layouts automatically adapt across Mobile, Tablet Portrait (centered max-width 600dp), and Tablet Landscape (dual-pane).
4. **"Ephemeral Public Sharing"**: For recruiter access, we generate offline QR codes via MediaStore Scoped Storage and sync an encrypted payload to Cloud Firestore with an automated 7-day expiration policy, viewable on any browser via our companion web app.
