# doctor

`harness doctor` checks that a repository's `harness` setup is complete and healthy, lists what is left to do, and
reports work that has landed on the default branch. It is read-only: it never edits files in the clone, never fires
lifecycle events and never runs actions. Agents use its JSON output to finish an adoption; CI can run it as an
optional gate.

## When to use it

- After `harness scaffold` or `harness scaffold --adopt`, to see what is left.
- After editing the profile, policy or hooks file, as a broader check than `render --check`.
- When something does not behave as expected: a hook does not fire, a lane selects nothing, Ralph refuses to start.
- In CI, optionally, next to `harness render --check`.
- Across many repositories with [`harness fleet doctor`](fleet.md).

## How it works

`doctor` runs every check below and prints one line per finding, grouped by check, followed by any notes.

| Check | Finds |
| --- | --- |
| Repository files | `.agents/harness.yaml`, `.agents/policy.yaml` or `.agents/hooks.yaml` missing, invalid, or with an unsupported `schema` |
| Rendered files | the same drift `harness render --check` reports |
| Manifest | owned paths missing, modified by hand, or present but no longer produced |
| Links | skill links that do not resolve |
| Commands | `commands`, lane `select`/`full`, `bootstrap`, validator and post-edit commands that call a mise task the repository does not define |
| Globs | `verify.app_paths`, `verify.lint_paths`, lane `required_when`, validator `when` and post-edit `paths` that match no tracked file |
| Default branch | `origin/HEAD` cannot be resolved, so the built-in force-push deny cannot name the branch |
| Tools | `claude`, `codex`, `bd` and `ralph-tui` versions outside the supported range, for the tools the profile uses |
| Skills | declared skill sources that are not installed (`modules.skills`) |
| Adoption to-do | the open items described in [scaffold](scaffold.md#the-to-do) |

Notes never change the exit code:

| Note | Shown when |
| --- | --- |
| Newer release available | the [update check](installation.md#update-notice) found one (at most one network check per day, cached; skipped offline or with `HARNESS_NO_UPDATE_CHECK=1`) |
| Newer embedded harness skill | the binary's skill text differs from `.agents/skills/harness/SKILL.md` only because the binary is newer; `harness render` refreshes it. This is not drift |
| Newer preset commit | a preset declared without `@<ref>` has a newer upstream commit than the one recorded in the manifest; `harness render --refresh` moves to it ([presets](presets.md#resolved-commits)) |
| Failed lifecycle run | the latest run of an event in `.work/lifecycle.jsonl` failed or was refused by a guard; the note names the event, the action and the exit status |
| Settings that are not policy | adoption left `defaultMode`, `additionalDirectories`, `approval_policy`, `sandbox_mode` or MCP server definitions in place |

### Landed branches

`doctor` looks at every branch `harness` recorded in `.work/`: managed worktrees, Ralph sessions, fleet deliveries. A
branch whose patches are all on the default branch (compared with `git cherry`, so a cherry-pick, a rebase or a squash
of the same patches counts) has **landed**. `doctor` lists each landed branch with the command that fires the
`landed` event for it, such as `harness worktree remove <name>` or `harness ralph clean`. It fires nothing itself:
`landed` fires from `harness worktree remove`, `harness ralph clean` and `harness lifecycle run landed`
([lifecycle](lifecycle.md#events)). A landed branch is not a finding and does not change the exit code. A CI clone
has no recorded branches, so `doctor` in CI lists none.

Tool versions come from a compatibility table built into each release. When an agent or tool release changes a file
format, `doctor` is where it shows first.

### JSON output

`harness doctor --json` prints one JSON document with the findings, the open adoption to-do items, the notes and the
landed branches,
each with its check, the path or entry it concerns, a message and the command or edit that resolves it. Agents read
it to plan the remaining work; nothing in it requires a person to parse prose.

## Reference

```text
harness doctor [--json]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--json` | off | Machine-readable output |

| Exit code | Meaning |
| --- | --- |
| 0 | No findings (notes may be present) |
| 1 | `doctor` itself could not run, for example outside a git repository |
| 2 | At least one finding or open to-do item |

Files read: the three repository files, the manifest and every owned path, native agent files, `.mise/tasks/` and
mise configuration (through `mise tasks`), the git index and branches, `.agents/skills/`, `.work/` session records,
`.work/lifecycle.jsonl`, `~/.cache/harness/`, upstream preset sources.
Files written: nothing in the clone; only the update-check cache under `~/.cache/harness/`.

## Examples

```sh
$ harness doctor
globs
  verify.lanes.e2e.required_when: src/**/routes/** matches no tracked file
skills
  lmarqs/skills is declared but not installed: run harness skills sync
to-do
  mise task "verify" collides; wrapper written as harness:verify
landed
  work/fix-invoice-rounding has landed: run harness worktree remove fix-invoice-rounding to fire landed
note
  harness v0.9.0 is available (you have v0.8.2)
  preset github:acme/agent-presets//harness has a newer commit (4e1a9c0); run harness render --refresh
$ echo $?
2
```

Agent loop to finish an adoption:

```sh
harness doctor --json      # read the items
# edit .agents/*.yaml, reconcile tasks, install skills
harness render
harness doctor             # repeat until exit 0
```

CI step, after `render --check`:

```yaml
      - run: harness doctor
        env:
          HARNESS_NO_UPDATE_CHECK: "1"
```

## Guidelines

- Do fix findings at their source: the repository files, the mise tasks, the globs. `doctor` only reports.
- Do run `doctor` before launching Ralph in a repository you have not used it in.
- Do read the `landed` actions with `harness lifecycle list` before running the command `doctor` prints for a landed
  branch: their automatic actions run.
- Don't gate CI on `doctor` until the adoption to-do is empty, or every pull request fails for an unrelated reason.

## Troubleshooting

| Finding | Meaning | Fix |
| --- | --- | --- |
| a glob matches no tracked file | a typo, or the directory moved | correct the glob in `.agents/harness.yaml` |
| command calls an undefined mise task | the task was renamed or never existed | fix the command in the profile, or add the task |
| `origin/HEAD` is not set | the clone was created without it | `git remote set-head origin --auto` |
| tool version unsupported | the installed `bd`, `ralph-tui` or agent is older or newer than this release supports | install a supported version, or update `harness` |
