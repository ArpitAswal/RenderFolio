---
name: tdd-workflow
description: Applies the RED -> GREEN -> REFACTOR cycle to business logic, state holders, and UI components.
---

# Skill: TDD Workflow

## Steps
1. **Define Test Scenarios**: Identify happy path, boundary cases, network/failure cases, and lifecycle/cancellation.
2. **RED**: Write a failing unit or widget test using Arrange → Act → Assert. Run the test runner to confirm it fails.
3. **GREEN**: Implement the minimal code required to pass. Confirm the test passes.
4. **REFACTOR**: Remove duplication, verify immutability, and ensure all tests stay green.