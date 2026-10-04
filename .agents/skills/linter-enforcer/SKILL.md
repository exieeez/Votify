---
name: linter-enforcer
description: >-
  Automatically verifies code for syntax errors, type issues, and style violations using project linters.
  Use immediately after editing files to ensure error-free compilation and clean formatting.
---

# Linter & Syntax Enforcer

This skill mandates checking all modified files with project-appropriate linters and compilers before considering a task complete.

## Verification Checklist by Stack

### Android / Kotlin:
1. Run syntax & compilation check:
   ```bash
   ./gradlew compileDebugKotlin
   ```
2. Run lint / ktlint if configured:
   ```bash
   ./gradlew lintDebug
   ```

### Web / TypeScript / JavaScript:
1. Run type checker:
   ```bash
   npx tsc --noEmit
   ```
2. Run ESLint:
   ```bash
   npx eslint --ext .ts,.tsx,.js,.jsx src/
   ```

### Python:
1. Run static analysis:
   ```bash
   ruff check .
   # or flake8 / mypy
   ```

## Rules
- Never ignore linter errors or warnings introduced by recent edits.
- Fix import errors, JVM signature clashes (`@JvmName`), type mismatches, and deprecations immediately.
