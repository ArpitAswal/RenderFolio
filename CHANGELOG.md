# Changelog

All notable changes to the **RenderFolio** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - Initial Production Release
**Release Date**: November 13, 2026

### 🚀 Added
- **On-Device Hybrid PDF Extraction Engine**: Complete local document parsing combining Apache PDFBox Android for native text extraction with Google ML Kit Text Recognition fallback for scanned documents, processing multi-page resumes up to 10MB in seconds.
- **Glassmorphic Structure Verification & Reordering**: Interactive editing screen allowing candidates to review extracted profile data, edit section headers, and drag-and-drop reorder sections via `ItemTouchHelper`.
- **Dynamic Custom Sections**: Capability to add arbitrary custom sections (e.g., Certifications, Publications, Volunteer Work) with live inline list updates.
- **Fluid Staggered Motion Visualizer**: Dedicated showcase activity (`ResumeVisualiserActivity`) featuring 6 specialized physics-driven motion drivers for Hero, Summary, Experience, Projects, Skills, and Education sections.
- **Offline QR Code Generation & Scoped Storage**: High-contrast ZXing 2D QR code generator embedded in a modal bottom sheet, saving shareable QR images directly to `Pictures/RenderFolio` via Android 10+ MediaStore Scoped Storage without requiring runtime permissions.
- **Firebase Cloud Sync & Web Companion**: Non-blocking Firestore synchronization with a 7-day auto-purge lifecycle, allowing public viewing on the companion web viewer (`renderfolio.web.app/r/{id}`) or via deep links (`renderfolio://r/{id}`).
- **3-Tier Responsive Adaptive Layouts**: Native support for Mobile (portrait & landscape), Tablet Portrait (`sw600dp` centered single-column, max-width 600dp), and Tablet Landscape (`sw600dp-land` dual-pane split).
- **Cold-Start Demo Showcase**: Built-in sample candidate profile ("Alex Morgan - Staff Android Architect") for instant verification without needing a PDF upload.

### 🔒 Security & Privacy
- **Local-First Privacy**: 100% on-device PDF parsing; resume files and raw document bytes are never uploaded to third-party AI endpoints or external servers.
- **Secure File Sharing**: Integrated AndroidX `FileProvider` with restricted XML path definitions to prevent external file URI exposure during QR sharing.
- **Cloud Firestore Security Rules**: Fine-grained Firestore rules enforcing strict validation, max-size payloads, and read-only access for expired shares.
