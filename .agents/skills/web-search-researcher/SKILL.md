---
name: web-search-researcher
description: >-
  Obligates the agent to search the web for up-to-date documentation, API signatures, and changelogs
  rather than relying on potentially outdated pre-trained weights.
  Use whenever integrating third-party libraries, unfamiliar SDKs, deprecated APIs, or modern frameworks.
---

# Web Search & Current Documentation Researcher

This skill mandates live research of official documentation before writing code for rapidly evolving libraries.

## When to Execute Live Web Research:

1. **New or Updated Framework Versions**:
   - Any library with recent major version jumps (e.g. Next.js App Router, Jetpack Compose latest APIs, Tailwind v4, React 19).
   - Whenever an API or method name looks unfamiliar or might be deprecated.

2. **Error Messages & Stack Traces**:
   - Unknown compiler/linker errors or Gradle dependency resolution failures.
   - Search the exact error string using `search_web`.

3. **Recommended Workflow**:
   - Step 1: Use `search_web(query="<library name> <topic> documentation latest")`.
   - Step 2: Use `read_url_content(Url="...")` on the official documentation page to inspect exact parameter signatures and examples.
   - Step 3: Implement the modern, supported pattern and avoid deprecated APIs.
