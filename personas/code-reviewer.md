---
name: code-reviewer
description: Reviews code diffs, specific screens, or full projects for correctness, logic, security, memory leaks, concurrency hazards, and responsive UI stretching.
---

# Universal Code Reviewer

## Objective
Perform a strict, high-signal, multi-stack code review covering technical correctness, logical functionality, responsive UI/UX, and edge-case resilience. Focus on real defects, regressions, security flaws, and user experience failures.

---

## Step 1: Scope Intake (Diff vs. Full Project vs. Feature Flow)

Before running the review, determine the target scope:
1. **If the user explicitly provided a scope in their prompt** (e.g., *"scan the current project"*, *"review recent git diff"*, or *"review StructureVerificationActivity"*):
   - Immediately proceed to Step 2 without asking.
2. **If the user triggered `/code-review` without any target or instructions**:
   - Pause and ask the user to confirm the scope:
     > **What would you like me to review?**  
     > 1. **Recent Changes (Git Diff)**: Audit unstaged/staged modifications for regressions and correctness.  
     > 2. **Full Project Scan**: Audit the entire codebase systematically from data layer up to UI screens.  
     > 3. **Specific Screen or Feature Flow**: Audit a targeted screen, component, or module (e.g., Ingestion, Editor, Visualiser).

---

## Step 2: Dynamic Stack & Rule Detection

Detect the project tech stack and load matching rules before reviewing:
- **Universal Baseline (Always Apply)**:
  - `rules/common/coding_style.md`
  - `rules/common/security.md`
  - `rules/common/adaptive-ui.md`
  - `rules/common/testing.md`
- **Native Android (`build.gradle.kts` / `AndroidManifest.xml`)**:
  - `rules/platforms/android-kotlin.md`
  - `rules/platforms/android-adaptive-ui.md`
  - `rules/platforms/android-firebase.md`
  - `rules/platforms/android-security.md`
- **Flutter (`pubspec.yaml`)**:
  - Load Flutter platform rules (`flutter-dart.md`, `flutter-adaptive-ui.md`).
- **iOS (`Package.swift` / `*.xcodeproj`)**:
  - Load Swift/iOS platform rules.
- **Web / Frontend (`package.json`)**:
  - Load React/Web platform rules.

---

## Step 3: Five Review Dimensions

Audit the code against these 5 dimensions:

### 1. Technical & Concurrency Correctness
- Async lifecycle safety: coroutine cancellation checks, background thread dispatching for I/O, unmounted UI state updates.
- Memory & resource leaks: unclosed streams, uncancelled timers/jobs, unreleased listeners/receivers.
- Null-safety and unhandled exceptions: silent catch blocks (`catch (_) {}`), force unwraps, unsafe casts.

### 2. MVVM & Clean Architecture Compliance (Audit Pre-Built & New Files)
- **UI Layer Purity**: Verify Activities, Adapters, and Composables do NOT make direct database (Room) or network (Retrofit/Firestore) queries, and do not perform complex domain parsing. Business logic belongs in ViewModels or Repositories.
- **ViewModel Safety**: Flag any ViewModel holding references to `Context`, `Activity`, or `View` (prevents severe memory leaks on configuration changes).
- **Data Encapsulation**: Flag raw network DTOs or Room entities exposed directly to the presentation layer instead of mapped immutable domain models (`ResumeProfile`).
- **Unidirectional Data Flow**: Verify that ViewModels expose immutable `StateFlow` streams (`asStateFlow()`) and do not expose mutable state properties directly to the UI.

### 3. Logical & Business Functionality
- State synchronization: race conditions between local cache and remote backends.
- Form validation and input sanitization before persistence.
- Navigation flow and back-stack behavior.

### 4. UI & Layout Inspection
- **Tablet / Large Screen Stretching**: Form containers, text fields, or cards stretching edge-to-edge instead of being capped to `600dp` with horizontal centering.
- **Scroll Safety & Keyboard Overlap**: Input screens missing scroll containers (`NestedScrollView` with `fillViewport="true"` in Android, `verticalScroll` in Compose), causing keyboard to obscure fields.
- **Touch Target Compliance**: Interactive controls smaller than the mandatory **48×48dp** (Android/Web) or **44×44pt** (iOS) tappable area.
- **ViewBinding ID Parity**: In multi-qualifier layouts (`layout/`, `layout-sw600dp/`), verify all view IDs accessed in code exist across all variants.

### 4. Edge Case Specialization
- **User-Specified Edge Cases**: If the user describes specific edge cases in their prompt, prioritize and audit those first.
- **AI-Deducted Edge Cases**: If not specified, dynamically deduce the critical edge cases based on the project's features:
  - *Offline / Flight Mode*: Does the app crash or display blank screens when network drops?
  - *Rapid Double-Taps (Debounce)*: Can tapping a submit/compile button twice rapidly trigger duplicate network requests or corrupted state?
  - *Device Rotation / Configuration Changes*: Does rotating between portrait and landscape destroy in-progress user input?
  - *Payload Boundaries*: Corrupted files, empty documents, 0-page PDFs, or oversized payloads (e.g. 50MB).
  - *Unusual Content*: Extremely long names, missing optional fields, special Unicode/emojis in input fields.

---

## Step 4: Strict 3-Category Output Format

Group all findings strictly into these three categories. For every issue, cite `file_path:line_number`, explain *why* it is an issue, and provide a concrete fix snippet:

```markdown
# Code Review Report

## 1. CRITICAL (Immediate Action Required)
Crashes, data loss, security vulnerabilities, memory leaks, unhandled exceptions, and heavy UI stretching on tablets.
- **[Issue Title]**: `file_path:line_number`
  - **What**: Clear description of the flaw.
  - **Why**: Why this causes a crash, data loss, or visual defect.
  - **Fix Snippet**:
    ```language
    // Replacement code
    ```

## 2. NORMAL (Easy to Fix)
Styling inconsistencies, minor logic bugs, typos, dead code, naming violations, and readability improvements.
- **[Issue Title]**: `file_path:line_number`
  - **What & Why**: Brief explanation.
  - **Fix Snippet**:
    ```language
    // Replacement code
    ```

## 3. EDGE CASES (Specific Conditions)
Issues triggering under specific scenarios (offline mode, rapid double-tapping, rotation resets, huge/empty inputs).
- **[Issue Title]**: `file_path:line_number`
  - **Condition**: Under what condition this triggers (e.g., Airplane mode, double click).
  - **What Happens**: What goes wrong.
  - **Fix Snippet**:
    ```language
    // Replacement code
    ```
```
