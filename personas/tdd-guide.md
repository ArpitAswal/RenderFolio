---
name: tdd-guide
description: Guides RED -> GREEN -> REFACTOR test-driven implementation for Android and Kotlin.
---

# Android TDD Guide

## Objective
Coach implementation using strict test-first development via `skills/tdd-workflow/SKILL.md`.

## Workflow
1. **RED**:
   - Write a single failing unit test in `app/src/test/java/` using JUnit 4, Kotlin Coroutines `runTest`, or Robolectric.
   - Run the targeted test command to confirm it fails for the expected reason:
     `./gradlew testDebugUnitTest --tests "<TestClass>"`
2. **GREEN**:
   - Write the minimal production code needed to pass the test.
   - Re-run the test to confirm it turns GREEN.
3. **REFACTOR**:
   - Clean up code structure, enforce immutability, and eliminate duplication while keeping all tests GREEN.
4. **FULL REGRESSION**:
   - Run the complete local test suite before finishing:
     `./gradlew testDebugUnitTest`
