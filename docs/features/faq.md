# FAQ

Commands on this page run as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a global
install, `harness <command>` is equivalent.

## General

### Does `harness` call a model or host agents?

No. It configures and constrains the tools that do (Claude Code, Codex, ralph-tui) and never calls a model. When a
Ralph run or an [`agent` action](lifecycle.md) needs an agent, `harness` starts the agent's own CLI, under the worker
contract.

### Is it related to Harness.io?

No. Harness.io is a CI/CD platform that also names its CLI `harness` and keeps pipelines in a `.harness/` directory.
`harness scaffold` refuses a repository whose `.harness/` belongs to another tool, and `./harnessw` always runs its own
pinned binary, whatever else is on `PATH` ([compatibility](compatibility.md#harnessio)).

### Does it use MCP servers?

The binary is never an MCP client. Work that needs an MCP tool is done by an agent that owns that connection and its
credentials: the coordinating agent, or an [`agent` action](lifecycle.md). Deterministic integrations use a service's
API through an `http` action, or its CLI through a `run` action.

### Do my teammates need to install it?

No. The repository's committed wrapper, `./harnessw`, downloads the pinned release the first time they run a command;
it needs a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`. Rendered files are committed, so their agents
get the same permissions and instructions before that. Agent hooks never download: until the release is cached they
print one line naming `./harnessw` and continue ([installation](installation.md#before-the-first-download)).

### What is `harnessw`?

A POSIX `sh` script committed at the repository root, in the pattern of Gradle's `gradlew`. It reads the release the
repository pins in `.harness/wrapper.yaml`, downloads it over HTTPS when it is not in the per-user cache, checks its
sha256 against the committed checksum, and runs it. It is the default entry point for people, agents, task shims
and hooks ([wrapper](wrapper.md)).

### Do I have to invoke guideline skills by hand in every session?

No. Declare them as standing context in the profile: always on, when the agent touches matching files, or when a
message contains certain words. `harness` injects them through the agents' hooks, and `./harnessw context show` shows
what a session would get and why ([context](context.md)).

### Do I need a global install?

No. A global install is a convenience for scaffolding new repositories and for `harness fleet`. Inside a pinned
repository it defers to the pin, so it never changes which release runs there
([installation](installation.md#global-install-optional)).

### Does CI need it?

No. Nothing in a repository's build, test, deploy or release depends on it. `./harnessw render --check` and
`./harnessw doctor` are optional CI steps; the wrapper downloads and verifies the pinned release
([wrapper](wrapper.md#ci)).

### Does it work offline?

Yes, once the pinned release is cached: the wrapper makes no network request after the first download. A machine that
never has network access uses `HARNESS_BINARY` or a pre-filled cache
([wrapper](wrapper.md#air-gapped-machines)).

### Which agents are supported?

Claude Code and Codex CLI, each through an adapter. A new agent needs a complete adapter in `harness` itself
([agents](agents.md#the-adapter-contract)).

### Does it work without mise?

Yes. Set `front_door: binary` (the default when the repository has no mise configuration) and call `./harnessw`
directly. The wrapper itself needs no mise.

### Does it support monorepos?

It operates at the git repository root: one profile, policy and hooks file per repository
([compatibility](compatibility.md#monorepos)).

### Does it run on Windows?

No. linux and macOS, on amd64 and arm64; the wrapper is a POSIX `sh` script.

## Files

### Why three files instead of one?

Each has one audience and one renderer: the profile describes the repository, the policy is reviewed as security
policy, and the hooks file describes editor feedback. Reviewers of a permission change read only `policy.yaml` and its
rendered diff.

### Why are there both `.harness/` and `.agents/`?

They have different owners. `.harness/` is `harness`'s own versioned configuration: the three repository files, the
pin, the manifest. `.agents/` is the cross-tool home of agent content (skills, memory, agent commands, the review
guide) that other tools read too and that stays meaningful without `harness`. State lives in neither
([concepts](concepts.md#two-directories-two-owners)).

### Can I edit `.claude/settings.json` by hand?

Yes, outside the keys `harness` owns (`permissions.*` and its hook entries). Everything else is preserved byte for byte.
Personal settings belong in `.claude/settings.local.json`, which `harness` never writes.

### I edited inside a managed block and my text disappeared.

`render` owns the text between markers. Write outside the markers; you may move the whole block anywhere in the file.

### Why are rendered files committed instead of generated on the fly?

So a clone works before its pinned release is downloaded, and so every permission change is visible as a diff in
review.

### How do I stop using it?

Two ways ([remove](remove.md)). `./harnessw remove` undoes the adoption: it deletes what `harness` added and writes
back the permission rules and hooks adoption imported. `./harnessw remove --eject` stops `harness` managing the
repository and keeps the result: the conventions in `AGENTS.md` and `.gitignore`, the permissions and the `raw` hooks
stay as hand-owned files. Both delete `harnessw` and `.harness/`.

### Does anything keep working after an eject?

Yes. Agents keep their permissions and `raw` hooks from the native files, and the convention blocks stay in
`AGENTS.md` and `.gitignore` as plain text, so people, agents, `bd` and ralph-tui still find worktrees and state where
they were. The tool blocks, which describe how to use `harness`, are removed, so nothing left names a command that is
gone. What stops is everything `harness` runs: the wrapper, verification receipts, lifecycle events, Ralph's worker
contract, injected standing context and the hooks that called `harness`.

## Policy

### Can a repository remove the built-in force-push deny?

No. The built-in base holds invariants that no repository file or preset can remove. It is deliberately small: only
what is unsafe in every repository.

### Why is my organization's deny list not built in?

The binary contains no organization-specific entry. Put shared entries in an [`extends` preset](presets.md#extends).

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
built-in invariant, such as allowing a force push to the default branch, is dropped, and it is reported
([scaffold](scaffold.md#the-equivalence-check)).

### How do I let agents use a read-only MCP tool without a prompt?

Add a native rule string under `shared.allow`, such as `"mcp__github__get_*"`. It renders verbatim for Claude Code;
Codex has no equivalent, and the `policy` block lists it as Claude-only.

## Verification and tasks

### Why does `verify` refuse to skip every lane?

A skip reason is free text that no check can judge. It may skip a lane, but an application change needs at least one
lane of real evidence.

### My receipt became invalid after I committed.

Committing does not invalidate it. Editing, adding, deleting or `chmod`-ing a changed file does, and so does rebasing
onto new commits ([verify](verify.md#freshness)).

### Are tasks the same as GitHub issues?

No. Tasks live in a clone-local tracker and are disposable; issues are the team's record. `harness` never writes to
GitHub issues or projects.

### I already sync Beads across machines. Will `harness` break that?

No. An existing `.beads/` is used as found; `harness` configures only a database it creates.

## Lifecycle and delivery

### Does `harness` push or open pull requests?

Not by itself. Everything that leaves the clone is a lifecycle action the repository or its preset declares, such as
`git push` and `gh pr create` under `deliver`. The binary ships no actions and contains no hosting-platform logic
([lifecycle](lifecycle.md)).

### Can it notify a chat channel when work finishes or an agent needs me?

Yes, through declared actions. An `http` action on `completed` or `blocked` posts to a webhook whose URL comes from an
environment variable, never from a committed file. The `attention` and `turn_end` hooks fire when an agent waits for a
person or ends a turn ([hooks](hooks.md)). Like every hook, they stay silent on a machine where the pinned release is
not cached yet.

### Is it safe to allow an agent to run `harness lifecycle run deliver`?

Allowing it allows everything the `deliver` actions do: they run as child processes of `harness`, outside the agent's
per-command permission checks. That is why scaffolded policies start with the command under `ask`. Review the actions
like code before moving it to `allow` ([safety](safety.md)).

### Can a Ralph worker deliver its own work?

No. The binary refuses `harness lifecycle run` inside a worker, whatever spelling the agent uses, and the built-in
base also denies `harness lifecycle` to every worker, rendered for every spelling. The launcher fires events and runs
their actions in its own process, never inside the worker, once the worker's verified commit has landed on the run
branch.

### A preset changed upstream, but `render` changed nothing.

`render` uses the preset commit recorded in the manifest, so every clone and CI render the same bytes. Run
`./harnessw render --refresh` to move to the newest commit; `doctor` notes when one exists
([presets](presets.md#resolved-commits)).

## Ralph

### Can Ralph workers run with `--dangerously-skip-permissions`?

Not in runs launched by `harness ralph`. The worker refuses every bypass flag and configuration key. Direct ralph-tui
runs outside `harness` are unaffected.

### Why does the worker replay verification instead of trusting the receipt?

The receipt is in the tree the agent can write, so it only selects what to check. The worker reruns those checks
against the final content, with rules read from the base commit, before it releases completion.

### Does `harness` open the pull request after a run?

The binary has no logic for pull requests or any hosting platform. A run ends on a local branch. What happens next is
the repository's `deliver` actions, for example `git push` and `gh pr create` from a preset: they run automatically
when declared with `auto: true`, or when a person or a coordinating agent runs `./harnessw lifecycle run deliver`. Most
runs need judgement first (which task commits go together, how to word them), so an agent arranges the commits with a
skill and then fires `deliver` ([ralph](ralph.md#delivery)).

### I run ralph-tui directly with my own config. Does `harness` interfere?

No. It never reads or writes a root `.ralph-tui/` unless you set `ralph.config: inherit`, and `ralph clean` touches only
sessions it recorded.

## Versions and updates

### Should we pin the version per repository?

Every adopted repository is pinned: `.harness/wrapper.yaml` names one release and the checksums of its binaries, and
both `./harnessw` and a global install run that release. Within one repository, every machine and CI job runs the
same version ([wrapper](wrapper.md)). Do not pin `harness` again in `mise.toml` or `.tool-versions`: `wrapper.yaml` is
the only pin, and `doctor` reports a second one as a finding.

### How do I upgrade?

`./harnessw wrapper --latest`, or `--version <tag>`, rewrites the pin; `./harnessw render` lets the new release
refresh what it renders; commit both. `harness fleet wrapper --version <tag> --deliver` does the same across many
repositories ([wrapper](wrapper.md#upgrading)).

### A teammate's `harness` refuses our repository.

With the pin in place this happens only when the pin is bypassed: `HARNESS_BINARY` names a release older than the
repository's `schema`, or their global binary is too old to defer to the pin. Unset `HARNESS_BINARY`, or run
`./harnessw <command>` ([installation](installation.md#version-skew)).

### Is it safe to run a binary that a committed script downloads?

The script downloads over HTTPS and runs a binary only after its sha256 matches the checksum committed in
`.harness/wrapper.yaml`. Those checksums were written by a `harness` binary only after it verified the release's
signed checksum file with the release keys it embeds, so a pin can name only a release the project signed. Trust
starts with the first binary you installed, and every later pin change is verified by a binary already trusted and
reviewed as a diff of `wrapper.yaml`. `doctor`, when online, re-verifies the pin against the signed file. Agent hooks
never download ([installation](installation.md#release-trust)).

### How do I turn off the update notice?

`export HARNESS_NO_UPDATE_CHECK=1`, or `session_start.update_notice: false` in `.harness/hooks.yaml` for agent sessions.
