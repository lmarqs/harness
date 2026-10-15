# Agent instructions

This repository builds the-cook, a recipe driven configuration tool. Read
`README.md`, the relevant page under `docs/features/` and the matching
standard under `docs/ux/` before changing a workflow or the command line. Keep Groovy cookbook recipes and Kotlin core or CLI changes in their
existing modules.

Run the focused module check while developing. Before delivery, run
`./gradlew check cliBuild jpackageTest` and `git diff --check`.
Review generated configuration and ownership records after changing recipes.
