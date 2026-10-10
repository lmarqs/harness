# FAQ

## Objective

**Problem.** Someone evaluating the tool, adopting it or meeting it in a teammate's repository usually has a few short,
specific questions: whether teammates must install anything, whether it pushes code or writes to the hosting platform,
whether an unattended agent can skip its permission checks, how to stop using it. The answers exist, but spread across
pages organised by feature rather than by question, so the reader has to read most of the documentation to answer one,
or guesses.

**Goal.** A reader with a common question finds a short, direct answer and the page that covers it in full, without
reading the feature pages first.

## Overview

Short answers to the questions people ask most often, grouped by subject. Each answer links the page that
covers it in full.

Commands on this page run as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a global
install, `the-cook <command>` is equivalent.

## General

### Does `the-cook` call a model or host agents?

No. It configures and constrains the tools that do (Claude Code and Codex CLI) and never calls a model. When a loop
runner, such as ralph-tui, needs an agent for a task, it calls `./cook worker <agent>`, which starts the agent's own
CLI under the [worker contract](ralph.md#the-worker-contract); an [`agent` action](lifecycle.md) does the same.

### Does it use MCP servers?

The binary is never an MCP client. Work that needs an MCP tool is done by an agent that owns that connection and its
credentials: the coordinating agent, meaning the agent session that runs `the-cook` commands for a person, or an
[`agent` action](lifecycle.md). Deterministic integrations use a service's API through an `http` action, or its CLI
through a `run` action.

### Do my teammates need to install it?

No. The repository's committed wrapper, `./cook`, downloads the pinned release the first time they run a command. It
needs a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`. Rendered files are committed, so their agents get the
same permissions and instructions before that. Agent hooks never download: until the release is cached they print one
line naming `./cook` and continue ([installation](installation.md#before-the-first-download)).

### What is `cook`?

A POSIX `sh` script committed at the repository root, in the pattern of Gradle's `gradlew`. It reads the release the
repository pins in `.cook/wrapper.yaml`, downloads it over HTTPS when it is not in the per-user cache, checks its
sha256 against the committed checksum, and runs it. It is the default entry point for people, agents, hooks and loop
runners ([wrapper](wrapper.md)).

### Do I have to invoke guideline skills by hand in every session?

No. Declare them as standing context in the profile: always on, when the agent touches matching files, or when a
message contains certain words. `the-cook` injects them through the agents' hooks, and `./cook context show` shows
what a session would get and why ([context](context.md)).

### Do I need a global install?

No. A global install is a convenience for scaffolding new repositories and for `the-cook fleet`. Inside a pinned
repository it defers to the pin, so it never changes which release runs there
([installation](installation.md#global-install-optional)).

### Does CI need it?

No. Nothing in a repository's build, test, deploy or release depends on it. `./cook render --check` and
`./cook doctor` are optional CI steps; the wrapper downloads and verifies the pinned release
([wrapper](wrapper.md#ci)).

### Does it work offline?

Yes, once the pinned release is cached: the wrapper makes no network request after the first download. A machine that
never has network access uses `COOK_BINARY` or a pre-filled cache
([wrapper](wrapper.md#air-gapped-machines)). Adoption works offline too: `the-cook scaffold --offline` writes everything
and leaves the pin [pending](wrapper.md#a-pending-pin) until a later `the-cook wrapper --version <tag>` with network
access verifies the release and records its checksums. Complete it before you push.

### Which agents are supported?

Claude Code and Codex CLI, through the builtin `claude` and `codex` harness recipes, which the project tests against
those agents. Another agent needs a harness recipe that fills the [harness contract](agents.md#the-harness-contract):
one a release ships, or one a team writes and the repository declares as a
[fetched recipe](recipes.md#fetched-recipes). The binary itself has no code for any agent.

### Why git, Claude Code and Codex, and not my version control system or agent?

Nothing in the binary sets them apart: it contains code for no external program, and git, Claude Code and Codex are
described by builtin [recipes](recipes.md) exactly like ralph-tui, Beads and mise
([ADR 0022](../adrs/0022-tool-integration.md)). They are the programs the release ships recipes for. Another
version control system needs a recipe that fills the
[version-control contract](recipes.md#the-version-control-contract), named with `vcs:` in the profile; another agent
needs a harness recipe. A team writes the recipe as data, publishes it, and each repository declares it under
`recipes:` with its `source`, `version` and `sha256`, then caches it with `./cook recipe fetch`. `the-cook doctor`
notes such a recipe as not verified by the release: the project's tests do not stand behind it, and its author owns
its floor rules.

### Does `the-cook` work with my loop runner or tracker?

Yes, with any that runs a command. The binary contains code for no external program; every program reaches a repository
as data: a recipe's entries, templates and renders, a declared command or a lifecycle action
([ADR 0022](../adrs/0022-tool-integration.md)). A loop runner names `./cook worker <agent>` as its agent command. A
tracker is updated by lifecycle actions on `start`, `completed` and `blocked` that read `COOK_TASK` and
`COOK_ACTOR` in a workspace created with `--task`. Two builtin recipes write those entries for you,
`./cook recipe apply ralph-tui` and `./cook recipe apply beads` ([recipes](recipes.md)). For any other tool,
declare the action yourself, for example a `completed` action that runs `gh issue close "$COOK_TASK"`, or write a
recipe that holds it.

### Does it work without mise?

Yes. The binary has no code for mise, a tool-version manager and task runner; the `mise` recipe describes it, and its
floor rule denies agents mise's `:::` task chaining in a repository that uses the recipe. mise is also one way to
install `the-cook` globally (`mise use -g github:lmarqs/the-cook@<tag>`); nothing else depends on it. Commands run as
`./cook <command>`, and the wrapper needs no mise ([compatibility](compatibility.md#mise)).

### Does it support monorepos?

It operates at the repository root, as the version-control recipe reports it: one profile, policy and hooks file per
repository ([compatibility](compatibility.md#monorepos)).

### Does it run on Windows?

No. It runs on linux and macOS, on amd64 and arm64, and the wrapper is a POSIX `sh` script.

## Files

### Why three files instead of one?

Each of `.cook/config.yaml` (the profile), `.cook/policy.yaml` (the policy) and `.cook/hooks.yaml` (the hooks
file) has one audience and one renderer: the profile describes the repository, the policy is reviewed as security
policy, and the hooks file describes editor feedback. Reviewers of a permission change read only `policy.yaml` and its
rendered diff.

### Why are there both `.cook/` and `.agents/`?

They have different owners. `.cook/` is `the-cook`'s own versioned configuration: the three repository files, the
pin, the manifest. `.agents/` is the cross-tool home of agent content (skills, memory, agent commands, the review
guide) that other tools read too and that stays meaningful without `the-cook`. State lives in neither
([concepts](concepts.md#two-directories-two-owners)).

### Can I edit `.claude/settings.json` by hand?

Yes, outside the keys `the-cook` owns (`permissions.*` and its hook entries). Everything else is preserved byte for
byte. Personal settings belong in `.claude/settings.local.json`, which `the-cook` never writes.

### I edited inside a managed block and my text disappeared.

`render` owns the text between markers. Write outside the markers; you may move the whole block anywhere in the file.

### Why are rendered files committed instead of generated on the fly?

So a clone works before its pinned release is downloaded, and so every permission change is visible as a diff in
review.

### How do I stop using it?

Two ways ([remove](remove.md)). `./cook remove` undoes the adoption: it deletes what `the-cook` added and writes back
the permission rules and hooks adoption imported. `./cook remove --eject` stops `the-cook` managing the repository
and keeps the result: the conventions in `AGENTS.md` and `.gitignore`, the permissions and the `raw` hooks stay as
hand-owned files. Both delete `cook` and the files in `.cook/` that `the-cook` owns or seeded, then `.cook/`
once it is empty. A file the manifest does not record, such as a `README.md` a person added to `.cook/`, is listed
and left.

### Does anything keep working after an eject?

Yes. Agents keep their permissions and `raw` hooks from the native files, and the convention blocks stay in `AGENTS.md`
and `.gitignore` as plain text, so people and agents still find workspaces and state where they were. The tool blocks,
which describe how to use `the-cook`, are removed, so nothing left names a command that is gone. What stops is
everything `the-cook` runs: the wrapper, verification receipts, lifecycle events, `the-cook worker` and its contract,
injected standing context and the hooks that called `the-cook`. Templates a recipe wrote stay as they are, so a
`.ralph-tui/config.toml` whose agent command is `./cook worker` needs a new agent command.

## Policy

### Can a repository remove the force-push deny?

No. It is a [floor rule](recipes.md#the-floor) of the git recipe: rendered into every agent's output on every render,
never written into `.cook/policy.yaml`, and out of reach of every repository file. Floor rules are deliberately
few: only what is unsafe in every repository that uses the program.

### Why is my organization's deny list not built in?

The binary contains no organization-specific entry. Organization content in the binary would tie its releases to one
organization, publish that organization's choices in an open-source tool, and make every other user carry entries
they never use ([ADR 0013](../adrs/0013-organization-content.md)). Write the deny list into each repository's
`.cook/policy.yaml`, as the next answer describes.

### How do we share our organization's choices across repositories?

Write them into each repository. What `the-cook` renders and runs in a repository comes only from that repository's
files, the release it pins and the fetched recipes it pins by hash, so a reviewer and an agent working there can read
every rule that applies, and the same commit renders the same bytes on every machine
([ADR 0013](../adrs/0013-organization-content.md)).

- **Choices that take judgement**, such as which MCP tools agents may read without asking, a deny on each repository's
  deploy command, a CI trigger in delivery or the task tracker its repositories use: publish a skill that describes
  them, install it with `npx skills add acme/agent-skills`, and have an agent following it change each repository's
  `.cook/policy.yaml`, `.cook/hooks.yaml` or profile. Each repository reviews the change as a diff, so two
  repositories can hold different entries for the same choice when they need to.
- **`the-cook` commands**, such as applying a stack recipe or moving to a new release: run them across repositories
  with [`the-cook fleet`](fleet.md), for example `the-cook fleet recipe apply node --yes --deliver` with a fleet file
  of Node repositories.
- **Stack facts**, such as how to lint, test and build a Node project: these are the builtin
  [stack recipes](recipes.md#stack-recipes), which `scaffold` applies when their detect rule matches and
  `the-cook recipe apply` applies on request.

### An allowed command still prompts. Why?

An `ask` or `deny` entry also matches (deny > ask > allow), or the agent does not trust the folder. The
`policy` block in `AGENTS.md` shows the effective policy and where each entry comes from.

### Why does Codex not enforce one of my denies?

Bare strings and native rule strings other than a plain `Bash(<words> *)` prefix are Claude-only; Codex cannot match
them. The `policy` block in `AGENTS.md` lists them as Claude-only. Express the rule as a prefix list
([policy](policy.md#entry-forms)).

### Does adoption lose any of our permission rules?

No. `scaffold --adopt` imports every project rule, keeps its decision, and runs an equivalence check per agent before
writing anything; a rule that would stop applying makes adoption exit 2 and name it. Only a rule that would weaken a
floor rule of a recipe in use, such as allowing a force push to the default branch, is dropped, and it is reported
([scaffold](scaffold.md#the-equivalence-check)).

### How do I let agents use a read-only MCP tool without a prompt?

Add a native rule string under `shared.allow`, such as `"mcp__github__get_*"`. It renders verbatim for Claude Code;
Codex has no equivalent, and the `policy` block lists it as Claude-only.

## Verification and tasks

### Why does `verify` refuse to skip every lane?

For an application change, one that touches a file matching `verify.app_paths`, each lane (a named family of test
commands in the profile) is either run or skipped with a reason, such as `--skip e2e="no route changed"`. A skip reason
is free text that no check can judge. It may skip a lane, but an application change needs at least one lane of real
evidence ([verify](verify.md#lane-rules)).

### My receipt became invalid after I committed.

Committing does not invalidate it. Editing, adding, deleting or `chmod`-ing a changed file does, and so does rebasing
onto new commits ([verify](verify.md#freshness)).

### Are tasks the same as GitHub issues?

A task is whatever the team's tracker holds, named by the free-form id given to `the-cook workspace create --task`: a
bead, a story in a JSON list, or an issue. The binary never writes GitHub issues or projects. A repository that wants an
issue closed when its task completes declares an action that does it ([tasks](tasks.md)).

### I already sync Beads across machines. Will `the-cook` break that?

No. Beads keeps its tasks in a `.beads/` database in the clone, and `the-cook` has no code for it: it never initialises,
configures or reads a Beads database. The `beads` recipe adds only lifecycle actions that run `bd update`, `bd close`
and `bd comments add` on the task a workspace is bound to ([compatibility](compatibility.md#beads)).

## Lifecycle and delivery

### Does `the-cook` push or open pull requests?

Not by itself. Everything that leaves the clone is a lifecycle action the repository declares in its own
`.cook/hooks.yaml`, such as `git push` and `gh pr create` under `deliver`. No action runs that the repository does
not declare, and the binary contains no hosting-platform logic ([lifecycle](lifecycle.md)).

### Can it notify a chat channel when work finishes or an agent needs me?

Yes, through declared actions. An `http` action on `completed` or `blocked` posts to a webhook whose URL comes from an
environment variable, never from a committed file. The `attention` and `turn_end` hooks fire when an agent waits for a
person or ends a turn ([hooks](hooks.md)). Like every hook, they stay silent on a machine where the pinned release is
not cached yet.

### Is it safe to allow an agent to run `the-cook lifecycle run deliver`?

Allowing it allows everything the `deliver` actions do: they run as child processes of `the-cook`, outside the agent's
per-command permission checks. That is why scaffolded policies start with the command under `ask`. Review the actions
like code before moving it to `allow` ([safety](safety.md)).

### Can a Ralph worker deliver its own work?

No. The binary refuses `the-cook lifecycle run` while `COOK_WORKER=1` is set, whatever spelling the agent uses, and
the binary also renders `[the-cook, lifecycle]` into every worker's denies, for each spelling `the-cook` renders. When
the checkout is bound to a task, `the-cook worker` fires `completed` and then `deliver`, with scope `task`, in its own
process, after the agent has exited and the replayed checks have passed. In a workspace not bound to a task, such as a
loop runner's, it fires no event.

## Ralph

### Can Ralph workers run with `--dangerously-skip-permissions`?

Not under `the-cook worker`. It refuses every argument the agent's harness recipe marks as a floor bypass argument, such
as `--dangerously-skip-permissions` for Claude Code or `--yolo` for Codex, with exit 1 naming the argument, so a loop
runner configured to pass one fails at its first task. An agent a person starts some other way, for example from their
own ralph-tui configuration, is outside `the-cook`, which neither detects nor prevents it
([ADR 0022](../adrs/0022-tool-integration.md)).

### Why does the worker replay verification instead of trusting the receipt?

The receipt, the record `the-cook verify` writes when the checks pass, is in the tree the agent can write, so it only
selects what to check. `the-cook worker` reruns those checks against the final content, with rules read from the base
commit, before it exits 0 and releases a loop runner's completion signal.

### Does `the-cook` open the pull request after a run?

The binary has no logic for pull requests or any hosting platform. In a loop runner's workspace, which is not bound to
a task, `the-cook worker` fires no event, and the run ends with its verified commits on the workspace's branch. Whoever
drives the run fires its events. Most runs need judgement first (which task commits go together, how to word them), so
a person or a coordinating agent arranges the commits with a skill, then runs
`./cook lifecycle run completed --scope run` and `./cook lifecycle run deliver --scope run`; the `deliver`
actions do the rest, for example `git push` and `gh pr create` declared in `.cook/hooks.yaml`
([ralph](ralph.md#delivery)). In a workspace created with `--task`, `the-cook worker` fires `completed` and, when the
work is concluded, `deliver` itself, both with scope `task`.

### I run ralph-tui directly with my own config. Does `the-cook` interfere?

No. `the-cook` has no code for ralph-tui and reads no ralph-tui configuration. The `ralph-tui` recipe writes
`.ralph-tui/config.toml` only when you apply it and the file is absent; an existing file is reported as
`kept .ralph-tui/config.toml: exists` and left as it is. Agents your configuration starts without `the-cook worker` get
no worker contract.

## Versions and updates

### Should we pin the version per repository?

Every repository set up with `the-cook scaffold` is pinned: `.cook/wrapper.yaml` names one release and the checksums
of its binaries, and both `./cook` and a global install run that release. Within one repository, every machine and
CI job runs the same version ([wrapper](wrapper.md)). `wrapper.yaml` is the only pin: a global install, through mise or
a download, defers to it.

### How do I upgrade?

`./cook wrapper --version <tag>`, or `--latest`, moves the pin and renders the repository with the new release in
the same change, so the pin and everything the new release renders differently arrive as one diff. Review it and commit
it. `the-cook fleet wrapper --version <tag> --deliver` does the same across many repositories
([wrapper](wrapper.md#upgrading)).

### We upgraded, but the new release's recipe entries are not in the repository.

A recipe's entries reach a repository only when someone applies it. The upgrade renders the repository's files with the
new release, so every recipe's renders and floor rules follow the new release, and leaves the entries as they are. Run
`./cook recipe apply node --dry-run` to see what the newer `node` recipe would add. Without `--dry-run` it shows
the same diff, asks, adds only what the repository lacks, and renders ([recipes](recipes.md#how-apply-works)).

### A teammate's `the-cook` refuses our repository.

With the pin in place this happens only when the pin is bypassed: `COOK_BINARY` names a release older than the
repository's `schema`, or their global binary is too old to defer to the pin. Unset `COOK_BINARY`, or run
`./cook <command>` ([installation](installation.md#version-skew)).

### Is it safe to run a binary that a committed script downloads?

The script downloads over HTTPS and runs a binary only after its sha256 matches the checksum committed in
`.cook/wrapper.yaml`. A `the-cook` binary wrote those checksums only after it verified the release's signed checksum
file with the release keys it embeds, so a pin can name only a release the project signed. Trust starts with the first
binary you installed, and every later pin change is verified by a binary already trusted and reviewed as a diff of
`wrapper.yaml`. `doctor`, when online, re-verifies the pin against the signed file. Agent hooks never download
([installation](installation.md#release-trust)).

### How do I turn off the update notice?

`export COOK_NO_UPDATE_CHECK=1`. The notice is one stderr line on explicit commands, at most once a day, plus a
`doctor` note. It never appears in agent sessions, because moving the pin is a reviewed decision, not one an agent takes
because it read a notice ([installation](installation.md#update-notice)).
