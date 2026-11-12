# Antigravity Workspace Instructions

This project is a **Native Android Application** (Kotlin + Jetpack Compose + Material 3 + Room + Firebase).

## 1. Primary Orchestration
- Always read and follow `@AGENTS.md` at the project root as the canonical source of engineering principles.

## 2. Common Rules
- Code style & size limits: `@rules/common/coding_style.md`
- Security & storage: `@rules/common/security.md`
- Testing & coverage: `@rules/common/testing.md`
- Development lifecycle: `@rules/common/development_workflow.md`
- Adaptive UI (universal): `@rules/common/adaptive-ui.md`

## 3. Platform Rules
- Android / Kotlin / Compose: `@rules/platforms/android-kotlin.md`
- Android Adaptive UI: `@rules/platforms/android-adaptive-ui.md`
- Android Firebase & Security: `@rules/platforms/android-firebase.md`
- Android Security Hardening: `@rules/platforms/android-security.md`

## 4. Personas
- Orchestrator: `@personas/orchestrator.md`
- Analyzer: `@personas/analyzer.md`
- Architect: `@personas/architect.md`
- Planner: `@personas/planner.md`
- Adaptive Screen Agent: `@personas/android-adaptive-screen-agent.md`
- TDD Guide: `@personas/tdd-guide.md`
- Code Reviewer: `@personas/code-reviewer.md`
- Security Reviewer: `@personas/security-reviewer.md`
- Build Error Resolver: `@personas/build-error-resolver.md`
- Technical Writer: `@personas/technical-writer.md`

## 5. Workflows (Slash Commands)
- /run: `@.agent/workflows/run.md`
- /analyze: `@.agent/workflows/analyze.md`
- /plan: `@.agent/workflows/plan.md`
- /adaptive-ui: `@.agent/workflows/adaptive-ui.md`
- /code-review: `@.agent/workflows/code-review.md`
- /fix: `@.agent/workflows/fix.md`
- /tdd: `@.agent/workflows/tdd.md`
- /build-fix: `@.agent/workflows/build-fix.md`
- /verify: `@.agent/workflows/verify.md`
- /security: `@.agent/workflows/security.md`
- /document: `@.agent/workflows/document.md`

## 6. Documentation Workflow (/document)
Generates or updates project documentation (`CHANGELOG.md`, `README.md`, `architecture.md`, `context.md`) by dynamically analyzing the repository's manifests, entry points, and system architecture.
