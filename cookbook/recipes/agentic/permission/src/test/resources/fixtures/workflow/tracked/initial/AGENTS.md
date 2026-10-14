# Agent instructions

This repository builds the-cook, a recipe driven configuration tool. Read
`README.md` and the relevant page under `docs/features/` before changing a
workflow. Keep Groovy cookbook recipes and Kotlin core or CLI changes in their
existing modules.

Run the focused module check while developing. Before delivery, run
`./gradlew check cliBuild cookbookBuild jpackageTest` and `git diff --check`.
Review generated configuration and ownership records after changing recipes.
