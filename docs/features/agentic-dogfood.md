# This repository's agent setup

The repository uses the independent `agentic/context` and `agentic/permission`
recipes. `AGENTS.md` is the editable, seeded project guidance. `CLAUDE.md`,
`.claude/settings.json`, `.codex/config.toml`, and
`.codex/rules/cookbook.rules` are rendered output. The two reviewed policy
sources are `.agents/policy.interactive.yaml` for Claude sessions and
`.agents/policy.unattended.yaml` for Codex unattended work. The filenames
describe intended use; the permission recipe selects a harness, not a role.

The interactive policy allows status, diff, log, and the project check, asks
before push, and denies hard reset. The unattended policy allows read-only Git
inspection and denies push and hard reset. The shared project instructions
live in `AGENTS.md`; policy `instructions` are not rendered by these recipes.
Neither policy changes agent bypass settings. Native settings and rules need
review when the policy changes.

From the repository root, generate or update the setup with:

```sh
./cook agentic/context --harness=claude --harness=codex
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex
```

On 2026-10-13, the first context command reported `Cooked AGENTS.md` and
`Cooked CLAUDE.md`. The permission commands reported `Cooked
.claude/settings.json`, then `Cooked .codex/config.toml` and `Cooked
.codex/rules/cookbook.rules`. After updating the policies, the two permission
commands reconciled the affected native files. Rerunning all three commands
reported `Already cooked.` each time, and SHA-256 hashes of the outputs and
`.cook/state.json` were unchanged.

Fresh-repository check, using the tracked recipes in this checkout and copies
of the two policy sources:

```sh
fixture=$(mktemp -d)
git -C "$fixture" init -q
mkdir -p "$fixture/.agents"
cp .agents/policy.interactive.yaml .agents/policy.unattended.yaml "$fixture/.agents/"
./cook agentic/context --repo "$fixture" --harness=claude --harness=codex
./cook agentic/permission --repo "$fixture" --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --repo "$fixture" --policy=.agents/policy.unattended.yaml --harness=codex
```

The fixture generated the same five outputs without `cook.groovy` or a starter
`.agents/policy.yaml`. Its ownership state listed the two context and two
permission harness applications plus their defaults. It owned `CLAUDE.md`,
Claude settings, Codex config, and Codex rules; it did not own the seeded
`AGENTS.md`. Rerunning the three commands reported `Already cooked.` and left
output and state hashes unchanged.

The complete project gate is
`./gradlew check cliBuild cookbookBuild jpackageTest`, followed by
`git diff --check`. In the approved implementation environment, run the Gradle
command with `GRADLE_USER_HOME=/tmp/the-cook-gradle-home` and the installed
Temurin 17 `JAVA_HOME`. The fixture tests in the context, permission, and init
modules also run through `cookbookBuild`.
