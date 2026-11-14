---
description: Perform a technical, logical functionality, UI/UX, and edge-case code review across diffs or entire projects
---

1. Load `personas/code-reviewer.md`, `rules/common/coding_style.md`, `rules/common/security.md`, and `rules/common/adaptive-ui.md`.

2. **Scope Intake**:
   - If the user specified a target (e.g., *"scan current project"*, *"review git diff"*, or a specific screen/file), proceed immediately.
   - If no scope was provided, ask the user to choose:
     1. **Recent Changes (Git Diff)**
     2. **Full Project Scan**
     3. **Specific Screen or Feature Flow**

3. **Stack & Platform Detection**:
   - Detect project type (`build.gradle.kts` → Android, `pubspec.yaml` → Flutter, `package.json` → Web, `Package.swift` → iOS).
   - Dynamically load the matching platform rules from `rules/platforms/`.

4. **Execute Review Across 5 Dimensions**:
   - Technical & Concurrency Correctness (lifecycle safety, memory leaks, unhandled exceptions).
   - MVVM & Clean Architecture Compliance (audit pre-built and new files for separation-of-concerns: no DB/network in UI, no Context in ViewModel, UDF state flow).
   - Logical & Business Functionality (state sync, validations, contract boundaries).
   - UI & Layout Inspection (tablet stretching, 48dp touch targets, keyboard overlap, scroll safety).
   - Edge Case Specialization (user-specified edge cases + AI-deducted edge cases like offline mode, double-taps, huge inputs).

5. **Format Findings**:
   - Output findings strictly grouped into:
     - `## 1. CRITICAL (Immediate Action Required)`
     - `## 2. NORMAL (Easy to Fix)`
     - `## 3. EDGE CASES (Specific Conditions)`
   - Every finding must include `file_path:line_number`, explanation, and actionable fix snippets.
