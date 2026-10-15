# This repository's agent setup

This page describes the agent setup this checkout tracks. [Issue #15](https://github.com/lmarqs/the-cook/issues/15)
tracks the intended engine.

## Setup files

The setup consists of seven tracked files:

| File | Role |
| --- | --- |
| `AGENTS.md` | Editable project guidance, shared by every agent |
| `CLAUDE.md` | Claude's import of `AGENTS.md` |
| `.claude/settings.json` | Claude permissions rendered from the interactive policy |
| `.codex/config.toml` | Codex sandbox and approval settings |
| `.codex/rules/cookbook.rules` | Codex command rules rendered from the unattended policy |
| `.agents/policy.interactive.yaml` | Reviewed policy source for Claude sessions |
| `.agents/policy.unattended.yaml` | Reviewed policy source for Codex unattended work |

The interactive policy allows status, diff, log, and the project check, asks before push, and denies hard reset. The
unattended policy allows read-only Git inspection and denies push and hard reset. The shared project instructions live
in `AGENTS.md`. Neither policy changes agent bypass settings.

The current CLI ships no recipe that maintains these files, so a policy edit is carried into the native files by hand
and reviewed with them. The [dev.thecook:cook:0.1.0:setup](../../cookbook/cook/setup.groovy) sketch shows the recipe
that declares this setup in the cookbook design.

## Project gate

The project gate is `./gradlew check cliBuild jpackageTest`, followed by `git diff --check`, as
[AGENTS.md](../../AGENTS.md) states. Run the Gradle command with `JAVA_HOME` set to a Temurin 17 JDK and
`GRADLE_USER_HOME=/tmp/the-cook-gradle-home`.
