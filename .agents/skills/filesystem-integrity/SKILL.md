---
name: filesystem-integrity
description: >-
  Inspects the project directory structure and checks neighboring files before modifications.
  Use whenever modifying core components, themes, models, or APIs to prevent breaking adjacent features.
---

# Filesystem & Project Integrity

This skill ensures the agent understands the holistic structure of the project and never modifies files in isolation.

## Mandatory Steps Before Editing:

1. **Map the File Hierarchy**:
   - Inspect the file tree and related directories:
     ```bash
     find . -path '*/src/*' -type f
     ```
   - Check where shared models, state holders, and UI components are placed.

2. **Grep for Dependents & References**:
   - Before renaming or altering any function, class, parameter, or preference key, search for all existing usages:
     ```bash
     grep -rn "TargetSymbol" src/
     ```

3. **Verify Neighboring Files**:
   - If editing a UI component (e.g. `SettingsComponents.kt`), review how consuming screens (e.g. `GeneralSettingsScreen.kt`, `PlayerSettingsScreen.kt`) invoke it.
   - Do not remove or change public signatures without updating all call sites or providing backwards-compatible overloads.

4. **Preserve Existing Structure**:
   - Do not create redundant copies or alternative files in wrong directories.
   - Respect project module boundaries, naming conventions, and package layouts.
