# Workspace Rules & Quality Enforcer

This project strictly enforces high engineering standards, zero code placeholders, and clean design.

---

## 1. Zero-TODO & Complete Implementations (Zero-Todo Enforcer)
- **STRICTLY FORBIDDEN**: Never leave `// TODO`, `// FIXME`, mock stubs, empty methods (`pass`, `throw NotImplementedError`), or truncated code snippets (`// ...`).
- Every feature, function, and edge case must be completely implemented and production-ready before marking a task as done.

---

## 2. Test-Driven Verification (TDD Runner)
- Whenever adding new features, fixing bugs, or refactoring business logic, write automated unit tests.
- Always execute tests in the terminal (`./gradlew test`, `npm test`, etc.) to verify that the implementation is 100% green before submitting.

---

## 3. Linter & Compilation Enforcement (Linter Enforcer)
- Run compilation checks (`./gradlew compileDebugKotlin`, `npx tsc --noEmit`, etc.) after writing or modifying code.
- Zero unresolved symbols, type mismatches, or platform signature clashes allowed.

---

## 4. Project & Filesystem Integrity (Filesystem MCP Protocol)
- Never modify files in isolation without inspecting callers and dependents.
- Always search the codebase for usages before modifying shared models, UI components, or parameters.
- Ensure all neighboring screens and modules remain functional.

---

## 5. Professional UI Design — Anti-"AI Slop" (Tailwind / Shadcn UI / M3)
- **NO Artificial Borders**: Do not place high-contrast borders or outlines around dark cards and sheets. Use subtle luminance tonal steps.
- **NO Cluttered Badges**: Do not wrap icons in colored circle wrappers unless representing an active selected state.
- **Native Design System**: Rely on established design tokens (Tailwind/Shadcn for web, clean Jetpack Compose Material 3 tokens for Android) with clean typography hierarchy.

---

## 6. Live Web Documentation (Web-Search Protocol)
- When dealing with third-party libraries, updated frameworks, or unfamiliar APIs, perform a live web search using available tools to read the official latest documentation instead of guessing.
