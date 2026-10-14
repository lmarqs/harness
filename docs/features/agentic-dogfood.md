# This repository's agent setup

This page describes the current implementation and this checkout's setup. The
[architecture](../architecture.md) defines the intended design, and
[issue #15](https://github.com/lmarqs/the-cook/issues/15) tracks implementing it.

The commands below use the current CLI and require the local JVM distribution;
see the [root build/help instructions](../../README.md#start-from-a-fresh-clone).

The repository uses the independent `agentic/context` and `agentic/permission`
recipes. `AGENTS.md` is the editable, seeded project guidance. `CLAUDE.md`,
`.claude/settings.json`, `.codex/config.toml`, and
`.codex/rules/cookbook.rules` are rendered output. The two reviewed policy
sources are `.agents/policy.interactive.yaml` for Claude sessions and
`.agents/policy.unattended.yaml` for Codex unattended work. The filenames
describe intended use; the permission recipe selects a harness.

The interactive policy allows status, diff, log, and the project check, asks
before push, and denies hard reset. The unattended policy allows read-only Git
inspection and denies push and hard reset. The shared project instructions
live in `AGENTS.md`; policy `instructions` are not rendered by these recipes.
Neither policy changes agent bypass settings. Native settings and rules need
review when the policy changes.

In a fresh clone, the seven tracked files above already exist but `.cook/state.json`
does not. From the repository root, claim the rendered blocks and keys once with
explicit adoption. Keep `AGENTS.md` and both policy sources in place:

```sh
./cook agentic/context --harness=claude --harness=codex --adopt=true
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude --adopt=true
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex --adopt=true
```

For ordinary updates after ownership is established, omit adoption:

```sh
./cook agentic/context --harness=claude --harness=codex
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex
```

The first three commands preserve the tracked source and rendered file bytes
and record ownership of the Claude import block, Claude permission keys, Codex
configuration keys, and Codex rules block. They do not claim `AGENTS.md`.
The next three commands report `Already cooked.` and leave outputs and
`.cook/state.json` unchanged. Managed edits still cause a drift error even
when `--adopt=true` is supplied.

To check the first-run workflow against a temporary destination using this
checkout's recipes and launcher, copy all seven tracked files without copying
`.cook/state.json`:

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

The resulting state lists both context and permission harness applications
and their defaults. Review the native files and `.cook/state.json` after any
policy edit.

The existing implementation project gate is
`./gradlew check cliBuild cookbookBuild jpackageTest`, followed by
`git diff --check`. In the approved implementation environment, run the Gradle
command with `GRADLE_USER_HOME=/tmp/the-cook-gradle-home` and the installed
Temurin 17 `JAVA_HOME`. The fixture tests in the context, permission, and init
modules also run through `cookbookBuild`.
