---
name: tdd-runner
description: >-
  Mandates writing unit tests and executing them in the terminal before completing tasks.
  Use whenever creating new features, fixing bugs, or refactoring business logic.
---

# TDD Runner (Test-Driven Development)

This skill obligates the agent to write automated unit tests and verify them in the terminal before delivering results.

## Workflow

1. **Step 1: Write Tests First (or Alongside Changes)**
   - Identify the expected inputs, outputs, and edge cases.
   - Create a test file (e.g., `*Test.kt`, `*.spec.ts`, `test_*.py`) that covers:
     - The happy path.
     - Known edge cases (empty collections, null/undefined, network timeouts, invalid arguments).
     - The exact bug scenario being fixed (regression test).

2. **Step 2: Implement Logic**
   - Write the cleanest, most focused production code to satisfy the test specifications.

3. **Step 3: Execute in Terminal**
   - Run the appropriate test runner command:
     - Android / Kotlin: `./gradlew testDebugUnitTest` or `./gradlew test`
     - Node / TypeScript: `npm test` or `pnpm test` or `npx jest` / `npx vitest run`
     - Python: `pytest` or `python -m unittest`
     - Go: `go test ./...`
     - Rust: `cargo test`

4. **Step 4: Verify & Fix Regressions**
   - Inspect terminal output: all tests must pass (exit code 0).
   - If tests fail, inspect the failure trace, refine the implementation, and re-run until all tests are green.
