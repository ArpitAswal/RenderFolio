# RenderFolio 📄✨

> **Interactive Digital Resume & Portfolio Visualizer for Android**  
> *Transform static PDF resumes into interactive, mobile-first showcase experiences with fluid motion, instant QR sharing, and web preview.*

[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2024%2B)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.4-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Database-Room%20%2B%20DataStore-F80.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![Cloud](https://img.shields.io/badge/Cloud-Firebase%20Firestore%20%2B%20Hosting-FFA611.svg?style=flat&logo=firebase)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat)](LICENSE)

---

## 🌟 Overview

**RenderFolio** (formerly ResumeShowcase) is an Android application engineered to bridge the gap between static documents and modern mobile interactions. By parsing standard PDF resumes directly on-device, RenderFolio extracts career milestones, technical competencies, work history, and custom achievements, rendering them into a recruiter-ready interactive portfolio.

Candidates can fine-tune extracted sections in a real-time verification editor, reorder elements, add custom categories, and generate branded QR codes for instant portfolio sharing. Recruiters can view the resume directly inside the Android app or via an interactive web companion hosted on Firebase Hosting without installing the app.

---

## 🚀 Key Features

* **⚡ On-Device PDF Extraction & Parsing**: Ingests both native digital text and scanned PDFs using Apache PDFBox Android with Google MLKit OCR fallbacks, processing multi-page resumes in seconds.
* **🛠️ Structure Verification & Interactive Editor**: Review and adjust parsed data before publishing. Re-order sections dynamically, edit details, and add custom categories (Certifications, Achievements, Publications, Volunteering).
* **🎨 Fluid Staggered Motion Engine**: Presentation layer powered by dedicated motion drivers (`HeroSectionMotionDriver`, `ProjectsMotionDriver`, `WorkExperienceMotionDriver`) offering smooth staggered springs and glassmorphism styling.
* **📲 Branded QR Sharing & Scoped Storage**: Generates high-resolution QR codes directly through ZXing, with zero-permission Scoped Storage image saving (`Pictures/RenderFolio`) and native Android share intents.
* **🌐 Web Companion (Recruiter View)**: Uploads encrypted, shareable resume payloads to Cloud Firestore with dedicated web previews (`public/index.html`) supporting dark theme and responsive layout.
* **🔒 Privacy-First Architecture**: Resume parsing occurs strictly on-device. Cloud synchronization is opt-in, backed by Firebase AppCheck and 7-day automated document expiration policies.

---

## 🏗️ Tech Stack & Architecture

RenderFolio follows official Android architecture guidelines: **Clean Architecture** with **MVVM** and **Unidirectional Data Flow (UDF)**.

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.4.x (Coroutines, Flow, Serialization) |
| **UI Framework** | Jetpack Compose + Material 3 + Custom XML/View Motion Drivers |
| **State Management** | Android Lifecycle `ViewModel` + `StateFlow` + `collectAsStateWithLifecycle` |
| **Document Processing** | Apache PDFBox Android, Google MLKit Text Recognition |
| **Local Persistence** | AndroidX Room DB (KSP codegen) + AndroidX DataStore Preferences |
| **Networking & API** | Retrofit 3.0, OkHttp 5.5, Moshi (KSP JSON Codegen) |
| **Cloud Infrastructure** | Firebase Firestore, Firebase Authentication, Firebase AppCheck, Firebase Hosting |
| **QR Code Generation** | ZXing Core (Zebra Crossing) |
| **Testing** | JUnit 4, Robolectric 4.17, KotlinX Coroutines Test |

---

## 📁 Repository Structure

```
ResumesShowcase/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── constant/         # Global constants, regex patterns & defaults
│   │   │   ├── data/             # Repositories (PDF Extraction, DataStore, Firestore)
│   │   │   ├── model/            # Immutable data models (ResumeModel, SavedShowcase)
│   │   │   ├── ui/               # Activities, Bottom Sheets, Adapters & Themes
│   │   │   │   ├── adapter/      # RecyclerView drag-and-drop adapters
│   │   │   │   ├── dialog/       # Custom section bottom sheet dialogs
│   │   │   │   ├── motion/       # Staggered View animation motion drivers
│   │   │   │   └── theme/        # Material 3 Color, Typography, and Theme
│   │   │   ├── util/             # QR Code Generator, Scoped Storage & Image utils
│   │   │   └── viewmodel/        # ResumeViewModel & UDF StateFlow holders
│   │   ├── res/                  # Adaptive layouts (phone, tablet sw600dp), drawables, styles
│   │   └── AndroidManifest.xml   # Manifest with deep links (renderfolio://r/{id})
│   └── build.gradle.kts          # App dependencies & signing configurations
├── public/                       # Standalone Web Companion (Firebase Hosting)
│   └── index.html                # Responsive web resume viewer for recruiters
├── rules/                        # Modular AI Engineering Rulebooks
│   ├── common/                   # Universal coding, security, and testing rules
│   └── platforms/                # Android-specific rules (Kotlin, Adaptive UI, Firebase)
├── personas/                     # Specialized AI agent roles (Architect, Reviewer, etc.)
├── skills/                       # Operational skills (TDD, Verification Loop, API Design)
├── .agent/workflows/             # Slash commands (/run, /fix, /security, /document, etc.)
└── build.gradle.kts              # Root build configuration
```

---

## ⚙️ Prerequisites & Setup

### Prerequisites
* **JDK**: Version 17 or 21 (configured in Android Studio)
* **Android Studio**: Ladybug (2024.2.1+) or newer
* **Android SDK**: `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37`
* **Google Services**: Ensure `google-services.json` is placed in `app/`

### Local Installation
1. **Clone the repository**:
   ```bash
   git clone https://github.com/<your-username>/RenderFolio.git
   cd RenderFolio
   ```
2. **Configure Local Properties**:
   Ensure `local.properties` exists in the project root with your Android SDK location:
   ```properties
   sdk.dir=/Users/<username>/Library/Android/sdk
   ```
3. **Build the Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Install on connected device or emulator**:
   ```bash
   ./gradlew installDebug
   ```

---

## 🧪 Testing & Verification

RenderFolio includes unit and Robolectric test suites validating parsing accuracy, model immutability, and section reordering:

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test suite (e.g. Work Experience Audit)
./gradlew testDebugUnitTest --tests "com.example.WorkExperienceAuditTest"

# Run Android Lint static analysis
./gradlew lintDebug

# Run Kotlin compilation check
./gradlew compileDebugKotlin
```

---

## 🔗 Deep Links & Web Integration

RenderFolio supports two deep link schemes:
* **Universal HTTPS**: `https://renderfolio.web.app/r/{shareId}`
* **Custom Scheme**: `renderfolio://r/{shareId}`

Opening a portfolio link on a mobile device with RenderFolio installed directly opens the interactive visualizer. On desktop or devices without the app, it gracefully falls back to the Firebase-hosted web viewer.

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
