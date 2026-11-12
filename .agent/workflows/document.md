---
description: Generates or updates core project documentation (README, CHANGELOG, architecture, context) dynamically.
---

1. Load `personas/technical-writer.md` and `personas/architect.md`.
2. Dynamically inspect the project:
   - Identify project type and platform manifests (`build.gradle*`, `package.json`, `pubspec.yaml`, `Cargo.toml`, `pyproject.toml`, `go.mod`, etc.).
   - Scan directory structure, core entry points, build/run scripts, and existing documentation.
3. If requested or unspecified, generate or update `CHANGELOG.md` following "Keep a Changelog" conventions based on current capabilities and commit history.
4. If requested or unspecified, generate or update `README.md` with dynamic setup instructions, prerequisites, architecture summary, and directory map.
5. If requested or unspecified, generate `architecture.md` detailing system design, state flow, module boundaries, and end-to-end data pipelines.
6. If requested or unspecified, load `skills/context-and-decisions/SKILL.md` to initialize or update the root `context.md` project memory.
7. Present the generated documentation files to the user for review.
