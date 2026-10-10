# doctor

## Objective

**Problem.** An adopted repository can be broken in ways no single file shows: a hook names a program that is not
installed, a path pattern matches no file, a teammate's machine lacks the release the repository pins, or an adoption
was left half finished. A developer sees only the symptom, such as a check that never runs or an unattended worker that
refuses every task, and an agent finishing an adoption has no list of what remains. Branches whose work already reached
the default branch pile up unnoticed.

**Goal.** A developer, an agent or a CI pipeline can tell, without changing anything, whether a repository's agent setup
is complete and working on this machine, what fixes each problem, and which finished branches are ready to clean up.

## Overview

`the-cook doctor` checks that a repository's `the-cook` setup is complete and healthy, lists what is left to do, and
reports work that has landed on the default branch. It is read-only: it never edits files in the clone, never fires
lifecycle events and never runs actions. Agents use its JSON output to finish an adoption; CI can run it as an
optional gate.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## When to use it

- After `the-cook scaffold` or `the-cook scaffold --adopt`, to see what is left.
- After editing the profile, policy or hooks file, as a broader check than `render --check`.
- When something does not behave as expected: a hook does not fire, a [lane](verify.md) selects nothing, an unattended
  [worker](ralph.md) refuses every task.
- In CI, optionally, next to `the-cook render --check`.
- Across many repositories with [`the-cook fleet doctor`](fleet.md).

## How it works

`doctor` runs every check below and prints one line per finding, grouped by check, followed by any notes.

| Check | Finds |
| --- | --- |
| Repository files | `.cook/config.yaml`, `.cook/policy.yaml` or `.cook/hooks.yaml` missing, invalid, or with an unsupported `schema` |
| Wrapper file | `.cook/wrapper.yaml` missing or invalid: no `schema`, a `version` that is not an exact tag, a `source` that is neither `github:<owner>/<repo>` nor an HTTPS base URL ([release source](wrapper.md#release-source)), or a checksum missing or not 64 hexadecimal characters for one of `linux-amd64`, `linux-arm64`, `darwin-amd64`, `darwin-arm64`. `checksums: pending` is valid and reported as the next row |
| Pending pin | `.cook/wrapper.yaml` has `checksums: pending`, written by `scaffold --offline`: no machine without the release in its cache can run `./cook` until `the-cook wrapper --version <the pinned tag>` completes it ([wrapper](wrapper.md#a-pending-pin)) |
| Wrapper script | `cook` missing, not executable, or different from the pinned release's template |
| Release signature | online only, at most once a day and cached: the pinned release's signed checksum file does not verify with the keys embedded in the running release, or its checksums differ from those in `.cook/wrapper.yaml` ([wrapper](wrapper.md#security)); a pending pin has none to compare and is reported as pending |
| Cached release | the pinned release is not in `$COOK_HOME` on this machine and `COOK_BINARY` is unset, so agent hooks skip until `./cook version` runs |
| Rendered files | the same drift `the-cook render --check` reports |
| Manifest | owned paths missing, modified by hand, or present but no longer produced |
| Links | skill links that do not resolve |
| Recipes | `recipes:` declares a fetched [recipe](recipes.md#fetched-recipes) that is not in the user's cache on this machine, so hooks and worker attempts that need it exit 3 until `./cook recipe fetch` runs; or a cached fetched recipe whose content does not match its `sha256` |
| Programs | a program that a recipe in use, a declared command or an action names and that is not on `PATH`: the version-control recipe's program (`git` for the git recipe), each agent's program, `commands`, lane `select`/`full`, `bootstrap`, validator and post-edit commands, and lifecycle `run` actions. The check needs no knowledge of the program ([ADR 0022](../adrs/0022-tool-integration.md)) |
| Detected programs | a program a recipe's detect rule finds in the repository while the recipe is not in `recipes:`, so the program runs without the recipe's entries and [floor rules](recipes.md#the-floor). Example: `mise.toml` is present and the mise recipe is not in use |
| Globs | `verify.app_paths`, `verify.lint_paths`, lane `required_when`, validator `when` and post-edit `paths` that match no tracked file |
| Default branch | the version-control recipe reports no default branch (with the git recipe: `origin/HEAD` cannot be resolved), so the git recipe's floor rule against a force push to the default branch cannot name the branch |
| Program versions | a program whose version, read with its recipe's version command, is outside the range the recipe's checks declare. Every recipe in use is checked the same way: `git` by the git recipe, Claude Code by the claude recipe, Codex by the codex recipe, mise by the mise recipe. A program no recipe in use describes has no version check |
| Policy | an entry that would weaken a [floor rule](recipes.md#the-floor) of a recipe in use, such as an `allow` for a force push to the default branch, a floor rule of the git recipe; `render` leaves it out and renders the floor rule, and the entry stays a finding until it is removed ([ADR 0011](../adrs/0011-safety-invariants.md)) |
| Context | invalid `context` declarations, a skill an entry names that does not exist under `.agents/skills/`, and a file an entry names that does not exist ([context](context.md#sources-and-trust)) |
| Adoption to-do | the open items described in [scaffold](scaffold.md#the-to-do) |

Notes never change the exit code:

| Note | Shown when |
| --- | --- |
| Newer release available | the [update check](installation.md#update-notice) found a release newer than the one `.cook/wrapper.yaml` pins, with a signed checksum file that verifies (at most one network check per day, cached; skipped offline or with `COOK_NO_UPDATE_CHECK=1`); the note names `./cook wrapper --version <tag>`, which moves the pin ([wrapper](wrapper.md#upgrading)) |
| Not the pinned release | `COOK_BINARY` makes `doctor` run as another release than the one `.cook/wrapper.yaml` pins: `running v0.8.2, the pin names v0.9.0; drift results may differ` |
| Failed lifecycle run | the latest run of an event in `.work/lifecycle.jsonl` failed or was refused by a guard; the note names the event, the action and the exit status |
| Settings that are not policy | adoption left `defaultMode`, `additionalDirectories`, `approval_policy`, `sandbox_mode` or MCP server definitions in place |
| `AGENTS.md` over budget | the managed blocks in `AGENTS.md`, the `context` block excepted, take more than `blocks.agents_md_budget` lines (default 40), or one of them more than 8; the note lists each block with its line count ([render](render.md#the-agentsmd-budget)) |
| Context over budget | the always-on context one role receives, `static` entries included, exceeds `context.budget`; the note lists every entry with its line count |
| Event an agent lacks | a declared hook or context trigger has no event in one of the repository's agents, such as `attention` in Codex, or Codex's `notify` key already runs a program of the repository's own, so nothing was rendered for that agent |
| Automatic agent actions | an `agent` action is declared with `auto: true`; each automatic run starts an agent, which costs tokens and does not give the same result twice |
| Recipe not verified by the release | `recipes:` declares a fetched recipe: the project's tests do not stand behind it, and its author owns its floor rules and supported versions: `recipe acme-deploy 1.2.0 is not verified by the release` |
| Another `the-cook` on `PATH` | the `the-cook` command on `PATH` is a different program with the same name; `./cook` is unaffected, only the global command is shadowed ([wrapper](wrapper.md#a-global-install-defers-to-the-pin)) |

### Landed branches

`doctor` looks at every branch `the-cook` recorded in `.work/`: managed workspaces and fleet deliveries. A branch whose
patches are all on the default branch (compared by patch equivalence through the version-control recipe's landed
operation, `git cherry` with the git recipe, so a cherry-pick, a rebase or a squash of the same patches counts) has
**landed**. `doctor` lists each landed branch with the command that fires the `landed` event for it, such as
`the-cook workspace remove <name>`. It fires nothing itself: `landed` fires from `the-cook workspace remove` and
`the-cook lifecycle run landed` ([lifecycle](lifecycle.md#events)). A landed branch is not a finding and does not change
the exit code. A CI clone has no recorded branches, so `doctor` in CI lists none.

Supported versions come from each recipe's checks. A builtin recipe's range is the one the release tested
([compatibility](compatibility.md#supported-program-versions)); a fetched recipe's range is what its author declares.
When an agent release changes a file format, `doctor` is where it shows first.

### JSON output

`the-cook doctor --json` prints one JSON document with the findings, the open adoption to-do items, the notes and the
landed branches, each with its check, the path or entry it concerns, a message and the command or edit that resolves it.
Agents read it to plan the remaining work; nothing in it requires a person to parse prose.

## Reference

```text
the-cook doctor [--json]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--json` | off | Machine-readable output |

| Exit code | Meaning |
| --- | --- |
| 0 | No findings (notes may be present) |
| 1 | `doctor` itself could not run, for example outside a repository the version-control recipe recognises |
| 2 | At least one finding or open to-do item |

Files read: the three repository files, `.cook/wrapper.yaml`, the manifest and every owned path (`cook`
included), native agent files, `PATH`, the version command of every recipe in use, the files each recipe's detect rule
names, the recipe cache, the index and branches as the version-control recipe reports them, `.agents/skills/`, the files
`context` entries name, the session records and `lifecycle.jsonl` under the state directory (default `.work/`),
`$COOK_HOME` (default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook`), and online the pinned release's signed
checksum file. Files written: nothing in the clone; only the update-check and signature-check results under
`$COOK_HOME`.

## Examples

```sh
$ ./cook doctor
programs
  .cook/hooks.yaml lifecycle.deliver open-pr names gh, which is not on PATH: install it, or remove the action
recipes
  mise detected; its recipe and its floor are not in use; run ./cook recipe apply mise
globs
  verify.lanes.e2e.required_when: src/**/routes/** matches no tracked file
context
  context.when skill timezone-handling does not exist under .agents/skills/: install it, or remove the entry
to-do
  instructions are in .agents/AGENTS.md: move them to the root AGENTS.md, which Codex reads
landed
  work/fix-invoice-rounding has landed: run ./cook workspace remove fix-invoice-rounding to fire landed
note
  the-cook v0.9.0 is available (this repository pins v0.8.2): run ./cook wrapper --version v0.9.0
  context for interactive sessions is 134 lines, over the budget of 120: coding-guidelines 96, house-rules 38
$ echo $?
2
```

Agent loop to finish an adoption:

```sh
./cook doctor --json      # read the items
# edit .cook/*.yaml, install missing programs and skills
./cook render
./cook doctor             # repeat until exit 0
```

CI step, after `render --check`:

```yaml
      - run: ./cook doctor
        env:
          COOK_NO_UPDATE_CHECK: "1"
```

## Guidelines

- Do fix findings at their source: the repository files, the globs, the programs installed on the machine. `doctor`
  only reports.
- Do run `doctor` before starting an unattended run ([ralph](ralph.md)) in a repository you have not used it in.
- Do read the `landed` actions with `./cook lifecycle list` before running the command `doctor` prints for a landed
  branch, because that command runs their automatic actions.
- Don't gate CI on `doctor` until the adoption to-do is empty, or every pull request fails for an unrelated reason.

## Troubleshooting

| Finding | Meaning | Fix |
| --- | --- | --- |
| a glob matches no tracked file | a typo, or the directory moved | correct the glob in `.cook/config.yaml` |
| a program is not on `PATH` | a command, lane, validator or action names a program this machine lacks, or a typo in its name | install the program, or fix the command in `.cook/config.yaml` or `.cook/hooks.yaml` |
| no default branch | with the git recipe, `origin/HEAD` is not set because the clone was created without it | `git remote set-head origin --auto` |
| program version unsupported | the installed program, such as `git`, `claude` or `codex`, is older or newer than its recipe supports | install a supported version, or, for a builtin recipe, move the pin with `./cook wrapper` to a release whose recipe supports it |
| a program is detected without its recipe | a detect rule matches, such as `mise.toml` for mise, and the recipe is not in `recipes:` | `./cook recipe apply mise` and review the diff |
| a fetched recipe is not cached | nothing on this machine has fetched it since `recipes:` declared it, or the cache was cleared | `./cook recipe fetch` |
| a fetched recipe's hash does not match | the content at its `source` differs from the `sha256` the profile pins | do not use it; check the source and the pinned hash with the recipe's author |
| `wrapper.yaml` invalid | hand-edited, or a `version` without matching `checksums` | re-pin with `./cook wrapper --version <tag>` and review the diff |
| `cook` differs from the template | the script was edited by hand | `./cook render` |
| `cook` is not executable | the executable bit was lost | `chmod +x cook`, then `git add --chmod=+x cook` |
| pinned release not cached | nothing has run `./cook` on this machine since the pin changed | `./cook version` |
| pin pending | the repository was scaffolded with `--offline` | with network access, `the-cook wrapper --version <the pinned tag>`, then commit `.cook/wrapper.yaml` and the manifest |
| release signature does not verify, or checksums differ | `wrapper.yaml` was edited by hand, or the release was replaced after it was pinned | do not run the release; re-pin with `./cook wrapper --version <tag>` and review the diff, and report a replaced release to the project |
| a policy entry weakens a floor rule | `.cook/policy.yaml` allows or asks for something a floor rule of a recipe in use denies; `render` already leaves it out | delete the entry from the policy |
| a context skill does not exist | the entry names a skill that was never installed under `.agents/skills/`, or was renamed | install it with the team's skills tool, such as `npx skills add acme/agent-skills`, and commit `.agents/skills/`; or fix the name in `context` |
