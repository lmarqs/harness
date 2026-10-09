# doctor

## Objective

**Problem.** An adopted repository can be broken in ways no single file shows: a hook names a task that no longer
exists, a path pattern matches no file, a teammate's machine lacks the release the repository pins, or an adoption was
left half finished. A developer sees only the symptom, such as a check that never runs or an unattended run that refuses
to start, and an agent finishing an adoption has no list of what remains. Branches whose work already reached the
default branch pile up unnoticed.

**Goal.** A developer, an agent or a CI pipeline can tell, without changing anything, whether a repository's agent setup
is complete and working on this machine, what fixes each problem, and which finished branches are ready to clean up.

## Overview

`harness doctor` checks that a repository's `harness` setup is complete and healthy, lists what is left to do, and
reports work that has landed on the default branch. It is read-only: it never edits files in the clone, never fires
lifecycle events and never runs actions. Agents use its JSON output to finish an adoption; CI can run it as an
optional gate.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

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
| Repository files | `.harness/config.yaml`, `.harness/policy.yaml` or `.harness/hooks.yaml` missing, invalid, or with an unsupported `schema` |
| Wrapper file | `.harness/wrapper.yaml` missing or invalid: no `schema`, a `version` that is not an exact tag, a `source` that is neither `github:<owner>/<repo>` nor an HTTPS base URL ([release source](wrapper.md#release-source)), or a checksum missing or not 64 hexadecimal characters for one of `linux-amd64`, `linux-arm64`, `darwin-amd64`, `darwin-arm64`. `checksums: pending` is valid and reported as the next row |
| Pending pin | `.harness/wrapper.yaml` has `checksums: pending`, written by `scaffold --offline`: no machine without the release in its cache can run `./harnessw` until `harness wrapper --version <the pinned tag>` completes it ([wrapper](wrapper.md#a-pending-pin)) |
| Wrapper script | `harnessw` missing, not executable, or different from the pinned release's template |
| One pin | `mise.toml`, `.mise.toml` or `.tool-versions` also pins `harness` (`github:lmarqs/harness` or `harness`). `.harness/wrapper.yaml` is the only pin; `harness` never edits the task runner's file, so remove the line yourself |
| Release signature | online only, at most once a day and cached: the pinned release's signed checksum file does not verify with the keys embedded in the running release, or its checksums differ from those in `.harness/wrapper.yaml` ([wrapper](wrapper.md#security)); a pending pin has none to compare and is reported as pending |
| Cached release | the pinned release is not in `$HARNESS_HOME` on this machine and `HARNESS_BINARY` is unset, so agent hooks skip until `./harnessw version` runs |
| Rendered files | the same drift `harness render --check` reports |
| Manifest | owned paths missing, modified by hand, or present but no longer produced |
| Links | skill links that do not resolve |
| Commands | `commands`, lane `select`/`full`, `bootstrap`, validator and post-edit commands that call a mise task the repository does not define |
| Globs | `verify.app_paths`, `verify.lint_paths`, lane `required_when`, validator `when` and post-edit `paths` that match no tracked file |
| Default branch | `origin/HEAD` cannot be resolved, so the built-in force-push deny cannot name the branch |
| Tools | `claude`, `codex`, `bd` and `ralph-tui` versions outside the supported range, for the tools the profile uses; `mise` when task shims are rendered (`front_door: mise`); the `skills` CLI when `modules.skills` is on |
| Policy | an entry that would weaken a built-in invariant, such as an `allow` for a force push to the default branch; `render` leaves it out and renders the invariant, and the entry stays a finding until it is removed ([ADR 0011](../adrs/0011-safety-invariants.md)) |
| Skills | declared skill sources that are not installed (`modules.skills`) |
| Context | invalid `context` declarations, a skill or file an entry names that does not exist, and a skill whose content does not match its hash in `skills-lock.json`, which is therefore not injected ([context](context.md#sources-and-trust)) |
| Adoption to-do | the open items described in [scaffold](scaffold.md#the-to-do) |

Notes never change the exit code:

| Note | Shown when |
| --- | --- |
| Newer release available | the [update check](installation.md#update-notice) found a release newer than the one `.harness/wrapper.yaml` pins, with a signed checksum file that verifies (at most one network check per day, cached; skipped offline or with `HARNESS_NO_UPDATE_CHECK=1`); the note names `./harnessw wrapper --version <tag>`, which moves the pin ([wrapper](wrapper.md#upgrading)) |
| Not the pinned release | `HARNESS_BINARY` makes `doctor` run as another release than the one `.harness/wrapper.yaml` pins: `running v0.8.2, the pin names v0.9.0; drift results may differ` |
| Failed lifecycle run | the latest run of an event in `.work/lifecycle.jsonl` failed or was refused by a guard; the note names the event, the action and the exit status |
| Settings that are not policy | adoption left `defaultMode`, `additionalDirectories`, `approval_policy`, `sandbox_mode` or MCP server definitions in place |
| `AGENTS.md` over budget | the managed blocks in `AGENTS.md`, the `context` block excepted, take more than `blocks.agents_md_budget` lines (default 40), or one of them more than 8; the note lists each block with its line count ([render](render.md#the-agentsmd-budget)) |
| Context over budget | the always-on context one role receives, `static` entries included, exceeds `context.budget`; the note lists every entry with its line count |
| Event an agent lacks | a declared hook or context trigger has no event in one of the repository's agents, such as `attention` in Codex, or Codex's `notify` key already runs a program of the repository's own, so nothing was rendered for that agent |
| Automatic agent actions | an `agent` action is declared with `auto: true`; each automatic run starts an agent, which costs tokens and does not give the same result twice |
| Another `harness` on `PATH` | the `harness` command on `PATH` is a different program with the same name; `./harnessw` is unaffected, only the global command is shadowed ([compatibility](compatibility.md#harnessio)) |

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

Files read: the three repository files, `.harness/wrapper.yaml`, the manifest and every owned path (`harnessw`
included), native agent files, `.mise/tasks/` and mise configuration (through `mise tasks`), `mise.toml`,
`.mise.toml` and `.tool-versions`, the git index and branches, `.agents/skills/` and `skills-lock.json`, the files
`context` entries name, the session records and `lifecycle.jsonl` under the state directory (default `.work/`),
`$HARNESS_HOME` (default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness`), and online the pinned release's signed
checksum file.
Files written: nothing in the clone; only the update-check and signature-check results under `$HARNESS_HOME`.

## Examples

```sh
$ ./harnessw doctor
pin
  mise.toml also pins harness: remove that line; .harness/wrapper.yaml is the only pin
globs
  verify.lanes.e2e.required_when: src/**/routes/** matches no tracked file
skills
  lmarqs/skills is declared but not installed: run ./harnessw skills sync
to-do
  mise task "verify" collides; wrapper written as harness:verify
landed
  work/fix-invoice-rounding has landed: run ./harnessw worktree remove fix-invoice-rounding to fire landed
note
  harness v0.9.0 is available (this repository pins v0.8.2): run ./harnessw wrapper --version v0.9.0
  context for interactive sessions is 134 lines, over the budget of 120: karpathy-guidelines 96, house-rules 38
$ echo $?
2
```

Agent loop to finish an adoption:

```sh
./harnessw doctor --json      # read the items
# edit .harness/*.yaml, reconcile tasks, install skills
./harnessw render
./harnessw doctor             # repeat until exit 0
```

CI step, after `render --check`:

```yaml
      - run: ./harnessw doctor
        env:
          HARNESS_NO_UPDATE_CHECK: "1"
```

## Guidelines

- Do fix findings at their source: the repository files, the mise tasks, the globs. `doctor` only reports.
- Do run `doctor` before launching Ralph in a repository you have not used it in.
- Do read the `landed` actions with `./harnessw lifecycle list` before running the command `doctor` prints for a landed
  branch: their automatic actions run.
- Don't gate CI on `doctor` until the adoption to-do is empty, or every pull request fails for an unrelated reason.

## Troubleshooting

| Finding | Meaning | Fix |
| --- | --- | --- |
| a glob matches no tracked file | a typo, or the directory moved | correct the glob in `.harness/config.yaml` |
| command calls an undefined mise task | the task was renamed or never existed | fix the command in the profile, or add the task |
| `origin/HEAD` is not set | the clone was created without it | `git remote set-head origin --auto` |
| tool version unsupported | the installed `bd`, `ralph-tui` or agent is older or newer than the pinned release supports | install a supported version, or move the pin with `./harnessw wrapper` |
| `wrapper.yaml` invalid | hand-edited, or a `version` without matching `checksums` | re-pin with `./harnessw wrapper --version <tag>` and review the diff |
| `harnessw` differs from the template | the script was edited by hand | `./harnessw render` |
| `harnessw` is not executable | the executable bit was lost | `chmod +x harnessw`, then `git add --chmod=+x harnessw` |
| pinned release not cached | nothing has run `./harnessw` on this machine since the pin changed | `./harnessw version` |
| pin pending | the repository was scaffolded with `--offline` | with network access, `harness wrapper --version <the pinned tag>`, then commit `.harness/wrapper.yaml` and the manifest |
| `harness` pinned in `mise.toml` too | the repository pinned the tool through mise before it had a wrapper, or adoption copied that pin | delete the line from the task runner's file; `.harness/wrapper.yaml` keeps the pin |
| release signature does not verify, or checksums differ | `wrapper.yaml` was edited by hand, or the release was replaced after it was pinned | do not run the release; re-pin with `./harnessw wrapper --version <tag>` and review the diff, and report a replaced release to the project |
| a policy entry weakens an invariant | `.harness/policy.yaml` allows or asks for something a built-in invariant denies; `render` already leaves it out | delete the entry from the policy |
| context skill does not match the lock | the skill was edited after the `skills` CLI installed it | reinstall it with `./harnessw skills sync`, or update the lock through the `skills` CLI |
