# This repository's agent setup

This page describes the [current implementation](../../README.md#current-implementation) as this checkout uses it;
[issue #15](https://github.com/lmarqs/the-cook/issues/15) tracks the intended engine. The commands below use the
current CLI and require the local JVM distribution; see
[start from a fresh clone](../../README.md#start-from-a-fresh-clone).

## Setup files

The repository uses the independent `agentic/context` and `agentic/permission` recipes from the
[agentic cookbook](../../cookbook/recipes/agentic/README.md). The setup consists of seven tracked files:

| File | Role |
| --- | --- |
| `AGENTS.md` | Editable project guidance; the context recipe seeds it, creating it when absent and leaving it unowned |
| `CLAUDE.md` | Recipe output |
| `.claude/settings.json` | Recipe output |
| `.codex/config.toml` | Recipe output |
| `.codex/rules/cookbook.rules` | Recipe output |
| `.agents/policy.interactive.yaml` | Reviewed policy source for Claude sessions |
| `.agents/policy.unattended.yaml` | Reviewed policy source for Codex unattended work |

The policy filenames describe intended use; the permission recipe selects an [agent harness](glossary.md)
with `--harness`.

The interactive policy allows status, diff, log, and the project check, asks before push, and denies hard reset. The
unattended policy allows read-only Git inspection and denies push and hard reset. The shared project instructions live
in `AGENTS.md`; these recipes leave policy `instructions` out of the native files. Neither policy changes agent bypass
settings. Native settings and rules need review when the policy changes.

## First run in a fresh clone

In a fresh clone, the seven tracked files already exist and `.cook/state.json`, the current implementation's ownership
record, does not. From the repository root, claim the recipe-written sections and keys once with explicit adoption
(`--adopt=true`), which records ownership of the existing content. Keep `AGENTS.md` and both policy sources in place:

```sh
./cook agentic/context --harness=claude --harness=codex --adopt=true
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude --adopt=true
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex --adopt=true
```

These commands preserve the tracked source and recipe output bytes and record ownership of the Claude import section,
Claude permission keys, Codex configuration keys, and Codex rules section. They do not claim `AGENTS.md`.

## Ordinary updates

After ownership is established, omit adoption:

```sh
./cook agentic/context --harness=claude --harness=codex
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex
```

After the first run, these commands report `Already cooked.` and leave outputs and `.cook/state.json` unchanged.
A managed edit, a manual change to content these recipes own, causes a drift error, even when `--adopt=true` is
supplied.

## Rehearse in a temporary fixture

To check the first-run workflow against a temporary destination using this checkout's recipes and launcher, copy all
seven tracked files without copying `.cook/state.json`:

```sh
fixture=$(mktemp -d)
mkdir -p "$fixture/.agents" "$fixture/.claude" "$fixture/.codex/rules"
cp AGENTS.md CLAUDE.md "$fixture/"
cp .agents/policy.interactive.yaml .agents/policy.unattended.yaml "$fixture/.agents/"
cp .claude/settings.json "$fixture/.claude/"
cp .codex/config.toml "$fixture/.codex/"
cp .codex/rules/cookbook.rules "$fixture/.codex/rules/"
./cook agentic/context --repo "$fixture" --harness=claude --harness=codex --adopt=true
./cook agentic/permission --repo "$fixture" --policy=.agents/policy.interactive.yaml --harness=claude --adopt=true
./cook agentic/permission --repo "$fixture" --policy=.agents/policy.unattended.yaml --harness=codex --adopt=true
./cook agentic/context --repo "$fixture" --harness=claude --harness=codex
./cook agentic/permission --repo "$fixture" --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --repo "$fixture" --policy=.agents/policy.unattended.yaml --harness=codex
```

The resulting state lists both context and permission harness applications and their defaults.

## Review after a policy edit

After any policy edit, review the four recipe output files and `.cook/state.json`: the Claude import section, Claude
permission keys, Codex configuration keys, and Codex rules section.

## Project gate

The project gate is `./gradlew check cliBuild cookbookBuild jpackageTest`, followed by `git diff --check`, as
[AGENTS.md](../../AGENTS.md) states. Run the Gradle command with `JAVA_HOME` set to a Temurin 17 JDK and
`GRADLE_USER_HOME=/tmp/the-cook-gradle-home`. The fixture tests in the context, permission, and init modules also run
through `cookbookBuild`.
