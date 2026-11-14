# Daily Engineering Commands Guide (`COMMANDS_GUIDE.md`)

> A practical guide for developers and AI agents explaining **which command to use, when to use it in daily production work, and why**, with concrete technical examples.

---

## 🧭 Quick Decision Matrix: "What do you want to do?"

| Situation / Problem | Recommended Command | What It Does (In 5 Words) |
| :--- | :--- | :--- |
| **"I have a big task and want everything handled end-to-end."** | `/run` | Autonomous end-to-end project manager |
| **"I have a new feature idea or architecture question."** | `/analyze` | Compares trade-offs with pros & cons |
| **"I know what to build, now I need a step-by-step roadmap."** | `/plan` | Detailed phased blueprint before coding |
| **"I want an inspection of flaws, bugs, and UI issues."** | `/code-review` | Non-destructive audit in 3 categories |
| **"I already know the exact bug; just fix the code directly."** | `/fix` | Fast, surgical code changes |
| **"I want to build a feature or fix a bug with unit tests first."** | `/tdd` | Test-first (Red ➔ Green ➔ Refactor) |
| **"My screen looks stretched or broken on tablets/foldables."** | `/adaptive-ui` | Phone, Tablet, & Foldable layout refactoring |
| **"I'm about to push code to GitHub or publish to production."** | `/security` | Scans for leaked keys, tokens, and secrets |
| **"Gradle build or Kotlin compiler failed with red errors."** | `/build-fix` | Surgically resolves compiler/lint breaks |
| **"I want to verify that all tests and linting pass."** | `/verify` | Automated quality & regression gate |
| **"I need to update README, CHANGELOG, or Architecture docs."** | `/document` | Auto-generates clean markdown documentation |

---

## 1. `/run` — The Master Orchestrator

### In Simple Words
Think of `/run` as your **Senior Project Manager**. Instead of you having to run 5 separate commands in order, you tell `/run` your goal. It asks you two quick questions (What to do? On which screens?), and then autonomously chains planning, coding, responsive design, testing, and verification from start to finish.

### When to Use in Daily Production
- When starting a major feature or doing a whole-app quality cleanup.
- When you want an agent to take ownership of an entire workflow without hand-holding.

### Technical Example
```bash
/run
```
> **Prompt Dialogue:**
> *Agent:* "What would you like me to do? (1. Audit & Fix, 2. New Feature, 3. Adaptive UI, 4. TDD, 5. Security Audit)"  
> *Developer:* `1 + 3 on StructureVerificationActivity`  
> *Result:* The agent audits the screen for bugs, fixes them, refactors the layout for tablets (sw600dp), and runs `./gradlew testDebugUnitTest` to prove it works.

---

## 2. `/analyze` — The System Architect

### In Simple Words
Think of `/analyze` as a **Senior Architect brainstorm**. Before anyone writes code or makes assumptions, `/analyze` looks at your existing codebase, challenges weak assumptions, and presents 2–3 different ways to solve the problem, complete with **Pros, Cons, and Trade-offs**. It writes **zero production code**.

### When to Use in Daily Production
- When you have a complex requirement (e.g., *"Should we use Room DB or DataStore for caching?"*).
- When you want to evaluate scalability and performance before committing to an approach.

### Technical Example
```bash
/analyze We want to add offline resume caching. Should we store parsed JSON strings directly in Room or create relational SQLite entity tables?
```
> **Result:** The agent outputs an Architectural Report comparing:
> - *Approach A (Full Relational Room Entities)*: Pros (type safety, indexed queries) vs Cons (boilerplate, KSP schema migrations).
> - *Approach B (TypeConverter with Moshi JSON)*: Pros (fast, no migration overhead) vs Cons (cannot query individual work experiences).  
> *Architect's Recommendation:* Approach B for fast prototyping, migrating to Approach A if search-by-skill is required.

---

## 3. `/plan` — The Blueprint Planner

### In Simple Words
Think of `/plan` as the **Construction Blueprint**. Once you've decided on the solution, `/plan` maps out the exact steps: which files will be created, which data models will change, what interface contracts look like, and in what order to write tests.

### When to Use in Daily Production
- Right before building any multi-file feature or non-trivial refactor.
- When you need a clear checklist to ensure nothing is forgotten.

### Technical Example
```bash
/plan Add a PDF export feature that lets users download their parsed showcase as a clean one-page PDF.
```
> **Result:** Generates an implementation plan artifact covering:
> 1. Impacted files: `PdfExporter.kt`, `ResumeVisualiserActivity.kt`, `AndroidManifest.xml` (FileProvider).
> 2. Data models: `ExportConfiguration(theme: Int, includePhoto: Boolean)`.
> 3. Phased steps: Domain logic first ➔ Unit tests ➔ UI Share Sheet integration.

---

## 4. `/code-review` — The Quality Inspector

### In Simple Words
Think of `/code-review` as a **Thorough Building Inspector**. It reads your code and flags bugs, memory leaks, security holes, and stretched layouts. It is **100% read-only**—it never modifies your files without permission. Findings are strictly grouped into **Critical**, **Normal**, and **Edge Cases**.

### When to Use in Daily Production
- Before opening a Pull Request (PR) on GitHub.
- After finishing a feature to catch edge cases you might have overlooked.

### Technical Example
```bash
/code-review Please scan the current project and raise any technical, logical functionality, and UI issues.
```
> **Result:** Produces a categorized report:
> - **1. CRITICAL**: Found unhandled `NullPointerException` risk in `StructureSectionsAdapter.kt:142` and tablet stretching in `activity_resume_visualiser.xml:49`.
> - **2. NORMAL**: Uses hardcoded `24dp` instead of `@dimen/screen_padding_horizontal`.
> - **3. EDGE CASES**: Double-clicking "Compile" button launches parallel network requests (missing debounce).

---

## 5. `/fix` — The Surgical Mechanic

### In Simple Words
Think of `/fix` as a **Fast Mechanic**. When you already know what is broken and don't need meetings, questionnaires, or 5-page plans, `/fix` jumps straight to the exact file, changes only the required lines, and runs the compiler to verify.

### When to Use in Daily Production
- Fixing an obvious bug discovered in a code review or bug report.
- Making quick adjustments (fixing a typo, adding a null check, updating a color).

### Technical Example
```bash
/fix Add debounce protection to btnMenuCompile in StructureVerificationActivity to prevent duplicate clicks.
```
> **Result:** The agent edits `StructureVerificationActivity.kt`, adds `lastClickTime` check or coroutine throttling, runs `./gradlew compileDebugKotlin`, and finishes.

---

## 6. `/tdd` — The Test-Driven Builder

### In Simple Words
Think of `/tdd` as the **Scientific Builder**. It follows the industry-standard **Red ➔ Green ➔ Refactor** cycle: it writes an automated unit test *first* (which fails because the code doesn't exist yet), then writes just enough code to make it pass, and then cleans up the code.

### When to Use in Daily Production
- When building core business logic, parsers, calculations, or critical features where failure is not an option.
- When resolving a regression so you can prove the bug can never happen again.

### Technical Example
```bash
/tdd Write a test for ResumeProfile JSON deserialization when the skills list is completely missing or null, then implement the fallback.
```
> **Result:**
> 1. Writes failing test in `app/src/test/java/com/example/ResumeProfileTest.kt` (RED).
> 2. Updates `ResumeProfile.kt` with `skills = emptyList()` default (GREEN).
> 3. Runs `./gradlew testDebugUnitTest` — 100% tests pass!

---

## 7. `/adaptive-ui` — The Responsive UI Specialist

### In Simple Words
Think of `/adaptive-ui` as the **Interior Designer for all Screen Sizes**. It makes your app look stunning on everything from a small budget smartphone, to a foldable phone unfolded, to a large 12-inch tablet in both Portrait and Landscape orientations.

### When to Use in Daily Production
- When cards or text fields stretch awkwardly across wide screens.
- When rotating a tablet cuts off buttons or erases user input.

### Technical Example
```bash
/adaptive-ui Refactor StructureVerificationActivity from Mobile view to Tablet Portrait and Tablet Landscape.
```
> **Result:**
> 1. Checks `AndroidManifest.xml` for `configChanges="orientation|screenSize"`.
> 2. Verifies `res/layout-sw600dp/` for centered single-column tablet portrait layout (max-width 600dp).
> 3. Creates `res/layout-sw600dp-land/` for a balanced two-column editing layout in landscape.
> 4. Runs `./gradlew compileDebugKotlin` to verify ViewBinding parity.

---

## 8. `/security` — The Vault Guard

### In Simple Words
Think of `/security` as the **Airport Security Checkpoint**. Before your code goes public to GitHub or the Google Play Store, `/security` scans every file in the repository to make sure no Google Cloud API keys, Firebase service account files, keystore passwords, or agent instructions are accidentally leaked.

### When to Use in Daily Production
- Right before your final `git push origin main`.
- When adding third-party SDKs or cloud integrations.

### Technical Example
```bash
/security
```
> **Result:**
> - Checks `git ls-files` to verify tracked files.
> - Confirms `.env`, `my-release-key.jks`, and `local.properties` are in `.gitignore`.
> - Verifies `android:allowBackup="false"` in `AndroidManifest.xml`.
> - Audits network calls for HTTPS enforcement.

---

## 9. `/build-fix` — The Emergency Doctor

### In Simple Words
Think of `/build-fix` as the **Emergency Room Doctor**. When Gradle fails, Kotlin throws red compile errors, or Room annotation processing (KSP) breaks, `/build-fix` analyzes the stack trace, identifies the root cause, and applies a surgical fix without using unsafe suppressions.

### When to Use in Daily Production
- When `./gradlew compileDebugKotlin` or `./gradlew build` crashes with compilation errors.
- When an SDK or library update breaks existing method signatures.

### Technical Example
```bash
/build-fix Fix the compilation error in MainActivity: Unresolved reference 'RenderFolioTheme'
```
> **Result:** Inspects imports, finds `Theme.kt`, updates the package reference, and re-runs the compiler until **BUILD SUCCESSFUL** is achieved.

---

## 10. `/verify` — The Final Quality Gate

### In Simple Words
Think of `/verify` as the **Flight Pre-Check**. It runs the entire local verification pipeline: compiling the code, running Android lint, and executing the full unit test suite to guarantee 0 regressions before committing.

### When to Use in Daily Production
- At the end of every feature or bug fix turn before telling the user you are done.
- Before merging a feature branch.

### Technical Example
```bash
/verify
```
> **Result:** Executes:
> 1. `./gradlew compileDebugKotlin` ➔ Passed
> 2. `./gradlew lintDebug` ➔ Passed
> 3. `./gradlew testDebugUnitTest` ➔ Passed (0 test failures)

---

## 11. `/document` — The Technical Writer

### In Simple Words
Think of `/document` as your **Project Biographer**. It inspects the codebase, manifests, and git history, and automatically generates or updates clean, professional documentation: `README.md`, `CHANGELOG.md`, `architecture.md`, and `context.md`.

### When to Use in Daily Production
- When rebranding or renaming an application.
- When completing a milestone or releasing a new version (`v1.0.0` ➔ `v1.1.0`).
- When onboarding new team members who need system architecture diagrams.

### Technical Example
```bash
/document Generate updated README, CHANGELOG, and Architecture diagrams reflecting RenderFolio branding and adaptive UI support.
```
> **Result:** Updates `README.md` with features and setup steps, adds new entry to `CHANGELOG.md`, and documents system layers in `architecture.md`.

---

## 💡 Typical Daily Workflow Example

Here is how a professional developer chains these commands together during a normal workday:

```
1. 💡 New Feature Idea       ➔   /analyze (Evaluate pros & cons)
2. 📐 Design & Plan          ➔   /plan (Produce the phased blueprint)
3. 🧪 Core Logic (TDD)        ➔   /tdd (Write test first, then implement logic)
4. 📱 Responsive UI Layout   ➔   /adaptive-ui (Adapt screen for phone, tablet, foldable)
5. 🔍 Quality Inspection     ➔   /code-review (Catch bugs, leaks, and edge cases)
6. 🔒 Pre-Push Security      ➔   /security (Ensure no secrets are leaked)
7. ✅ Final Quality Gate     ➔   /verify (Run full test suite & lint)
8. 📝 Documentation          ➔   /document (Update CHANGELOG & README)
```

