---
name: zero-todo-enforcer
description: >-
  Strictly forbids the agent from leaving TODO comments, placeholder stubs, or incomplete code blocks.
  Use when writing, refactoring, or generating code to ensure 100% full, production-ready implementations.
---

# Zero-Todo Enforcer

This skill mandates that all code produced by the agent is complete, robust, and free of placeholders.

## Strict Rules

1. **NO `// TODO` or `// FIXME` Comments**:
   - Never defer logic to future work unless explicitly instructed by the user.
   - If an edge case or error path exists, implement the proper handling immediately.

2. **NO Truncated Code or Ellipses**:
   - Never write `// ... rest of code goes here` or placeholder ellipses.
   - When modifying files, always supply the complete replacement chunks.

3. **NO Empty Stubs or Mock Methods**:
   - Every function and method must contain real, working logic.
   - Never leave methods with `throw UnsupportedOperationException()` or `pass` without actual functionality.

4. **Self-Audit Checklist Before Reporting Task Completion**:
   - Search the modified files for any occurrences of `TODO`, `FIXME`, `STUB`, or temporary mock values.
   - Ensure all parameters, return types, and side effects are completely wired up.
