---
description: Generates or updates core project documentation (README, CHANGELOG, architecture, context) dynamically without AI meta-bloat.
---

1. Load `personas/technical-writer.md` and `personas/architect.md`.

2. Dynamically inspect the project:
   - Identify project type and check platform manifests (`app/build.gradle.kts`, `package.json`, `pubspec.yaml`, etc.) to get the **true active version number** (`versionName`).
   - Scan directory structure, core entry points, build/run scripts, and existing documentation.

3. **Generate or Update `CHANGELOG.md`**:
   - Follow the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) standard.
   - **App Features Only**: Document strictly user-facing features, technical engine capabilities, UI motions, and security. **Never** include internal AI prompt files, `.agent/` workflows, `.codex/` configurations, or persona notes.
   - **True Versioning**: Match the exact `versionName` in the project manifest. If v1.0.0 (initial release), produce only `## [1.0.0] - Initial Production Release`. Never fabricate past releases or fake calendar dates.
   - Categorize entries into `### 🚀 Added`, `### 🔄 Changed`, `### 🐛 Fixed`, and `### 🔒 Security`.

4. **Generate or Update `README.md`**:
   - Provide an engaging, professional entry point:
     1. Centered header with app logo placeholder, title, subtitle, platform badges, and empty release download link (`<a href="">...</a>`).
     2. High-level Problem ➔ Solution pitch for developers, recruiters, and interviewers.
     3. Dedicated Mobile & Tablet Adaptive Showcase (Phone locked to portrait in code, Tablet Portrait `sw600dp`, Tablet Landscape `sw600dp-land`).
     4. Core product capabilities and deep-dive sections (Advanced Engineering, Security & Storage Architecture, Offline-First Resilience).
     5. Clean application-only directory tree (strictly excluding internal AI `.agent/`, `.codex/`, `personas/`, `rules/`).
     6. Quick setup and verification commands.

5. **Generate or Update `architecture.md`**:
   - Detail the system design story behind the UI across 5 mandatory sections:
     1. Executive Architecture Pitch with full ASCII/Mermaid boundary diagram.
     2. Screen-by-Screen User Journey tracing data movement across Ingestion, Editor, Visualizer, and Web.
     3. The 4 Core Subsystems (Document Ingestion Engine, Reactive State/Cache, Motion & 3-Tier Adaptive UI, Ephemeral Cloud/Web Sharing).
     4. Key Architectural Trade-offs (Privacy via on-device parsing, zero-permission storage, 7-day TTL).
     5. Interview / System Design Cheat-Sheet enabling developers to explain the architecture out loud.

6. **Generate or Update `context.md`**:
   - Maintain active project memory under 75 lines with a mandatory 4-part structure:
     1. Stack & Architecture (SDKs, libraries, tools).
     2. Key Architectural Decisions (ADR Table with Decision + Rationale).
     3. Active Conventions & Invariants (Immutability, Main Safety, ViewBinding Parity, Rotation Safety).
     4. Current Milestone & Exact Verification Commands.

7. Present the generated or updated documentation files to the user for review.
