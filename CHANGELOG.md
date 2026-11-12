# Changelog

All notable changes to the **RenderFolio** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Changed
- Rebranded application from `ResumeShowcase` to `RenderFolio` across Android resources, manifests, themes, QR sharing, and deep links (`renderfolio://r/{id}`).
- Consolidated and hardened cross-platform AI engineering toolkits (`rules/`, `personas/`, `skills/`, `.agent/workflows/`, and `.codex/`).
- Added dedicated Android security rules (`rules/platforms/android-security.md`) and pre-publish security audit workflow (`/security`).
- Added dedicated quick-fix workflow (`/fix`) bypassing redundant intake questionnaires for known defects.

### Added
- Comprehensive project documentation: [README.md](README.md), [architecture.md](architecture.md), [context.md](context.md), and [CHANGELOG.md](CHANGELOG.md).
- Support for `renderfolio.web.app` and `renderfolio://r/{id}` deep linking with backward-compatible fallback for `resumeshowcase`.

---

## [1.2.0] - 2026-11-12

### Added
- **Dark Theme Visualizer**: Full high-contrast dark theme support across all interactive portfolio sections.
- **Monogram Avatar & Web Parity**: Dynamic initial monogram generation for candidate profiles with Stitch design system parity in `public/index.html`.
- **Dynamic Structure Reordering**: Support for arbitrary section ordering and real-time drag-and-drop resequencing in `StructureVerificationActivity`.
- **Multi-Resume Parsing Audits**: Unit test coverage across diverse PDF resume formats, dynamic heading extraction, and custom section schemas.

### Changed
- Enhanced custom section input dialogs with preset templates (Certifications, Publications, Achievements, Volunteer Work).
- Streamlined `StructureSectionsAdapter` with smoother reorder animations and item removal transitions.

---

## [1.1.0] - 2026-11-05

### Added
- **Fluid Staggered Motion Engine**:
  - `HeroSectionMotionDriver`: Spring-driven hero profile intro with contact chip stagger.
  - `SummarySectionMotionDriver`: Smooth expand/collapse animations with character boundary safety.
  - `WorkExperienceMotionDriver`: Chronological career timeline animations with company chip reveals.
  - `ProjectsMotionDriver`: Interactive card tilt, tech stack tags, and live repository links.
  - `EducationMotionDriver`: Academic credentials and degree honors presentation.
  - `TechnicalSkillsMotionDriver`: Dynamic categorized skill pills with interactive filtering.
- **QR Code Sharing & Cloud Persistence**:
  - Offline-first ZXing QR code generator with Android 10+ Scoped Storage integration (`Pictures/RenderFolio`).
  - Seamless Cloud Firestore sync for public portfolio hosting with 7-day auto-expiry lifecycle rules.
  - Web companion viewer in `public/index.html` allowing recruiters to view candidate portfolios on any browser.

### Security
- Added secure `FileProvider` setup to prevent external URI file exposure.
- Firestore security rules configured to restrict write permissions to authenticated candidate owners.

---

## [1.0.0] - 2026-10-30

### Added
- **Baseline Application Release**:
  - Native Android app (Kotlin, compileSdk 37, minSdk 24).
  - On-device PDF extraction pipeline combining Apache PDFBox Android and MLKit Text Recognition.
  - Cold start landing screen with interactive resume upload dropzone and demo showcase loader.
  - Verification & editing screen with Material 3 glassmorphic loading dialog.
  - Initial Material 3 UI theme with dynamic system light/dark switching.
  - Local persistence using AndroidX DataStore for saved portfolios.

