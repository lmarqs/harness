# FAQ

## General

### Does `harness` call a model or host agents?

No. It configures and constrains the tools that do (Claude Code, Codex, ralph-tui) and never calls a model.

### Do my teammates need to install it?

No. Rendered files are committed, so their agents get the same permissions and instructions. Hooks print
`harness: not installed, hook skipped` and continue. They need the binary only to run `harness` commands themselves:
render, verify, task tracking, Ralph.

### Does CI need it?

No. Nothing in a repository's build, test, deploy or release depends on it. `harness render --check` and
`harness doctor` are optional CI steps ([render](render.md#drift-check)).

### Which agents are supported?

Claude Code and Codex CLI, each through an adapter. A new agent needs a complete adapter in `harness` itself
([agents](agents.md#the-adapter-contract)).

### Does it work without mise?

Yes. Set `front_door: binary` (the default when the repository has no mise configuration) and call `harness`
directly.

### Does it support monorepos?

It operates at the git repository root: one profile, policy and hooks file per repository
([compatibility](compatibility.md#monorepos)).

### Does it run on Windows?

No. linux and macOS, on amd64 and arm64.

## Files

### Why three files instead of one?

Each has one audience and one renderer: the profile describes the repository, the policy is reviewed as security
policy, and the hooks file describes editor feedback. Reviewers of a permission change read only `policy.yaml` and its
rendered diff.

### Can I edit `.claude/settings.json` by hand?

Yes, outside the keys `harness` owns (`permissions.*` and its hook entries). Everything else is preserved byte for byte.
Personal settings belong in `.claude/settings.local.json`, which `harness` never writes.

### I edited inside a managed block and my text disappeared.

`render` owns the text between markers. Write outside the markers; you may move the whole block anywhere in the file.

### Why are rendered files committed instead of generated on the fly?

So a clone works without the binary, and so every permission change is visible as a diff in review.

### How do I stop using it?

`harness remove`. It deletes exactly what the manifest lists and leaves `.claude/settings.json` permissions as plain
rules ([remove](remove.md)).

## Policy

### Can a repository remove the built-in force-push deny?

No. The built-in base holds invariants that no repository file or preset can remove. It is deliberately small: only
what is unsafe in every repository.

### Why is my organization's deny list not built in?

The binary contains no organization-specific entry. Put shared entries in an [`extends` preset](presets.md#extends).

### An allowed command still prompts. Why?

An `ask` or `deny` entry also matches (deny > ask > allow), or the agent does not trust the folder. The
`policy-summary` block in `AGENTS.md` shows the effective policy and where each entry comes from.

### Why does Codex not enforce one of my denies?

Bare strings and native rule strings other than a plain `Bash(<words> *)` prefix are Claude-only; Codex cannot match
them. The `policy-summary` block lists them as Claude-only. Express the rule as a prefix list
([policy](policy.md#entry-forms)).

### Does adoption lose any of our permission rules?

No. `scaffold --adopt` imports every project rule, keeps its decision, and runs an equivalence check per agent before
writing anything; a rule that would stop applying makes adoption exit 2 and name it. Only a rule that would weaken a
built-in invariant, such as allowing a force push to the default branch, is dropped, and it is reported
([scaffold](scaffold.md#the-equivalence-check)).

### How do I let agents use a read-only MCP tool without a prompt?

Add a native rule string under `shared.allow`, such as `"mcp__github__get_*"`. It renders verbatim for Claude Code;
Codex has no equivalent, and the `policy-summary` block lists it as Claude-only.

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

### Is it safe to allow an agent to run `harness lifecycle run deliver`?

Allowing it allows everything the `deliver` actions do: they run as child processes of `harness`, outside the agent's
per-command permission checks. That is why scaffolded policies start with the command under `ask`. Review the actions
like code before moving it to `allow` ([safety](safety.md)).

### Can a Ralph worker deliver its own work?

No. `harness lifecycle` is denied to every worker by the built-in base. The launcher fires events and runs their
actions in its own process, never inside the worker, once the worker's verified commit has landed on the run branch.

### A preset changed upstream, but `render` changed nothing.

`render` uses the preset commit recorded in the manifest, so every clone and CI render the same bytes. Run
`harness render --refresh` to move to the newest commit; `doctor` notes when one exists
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
when declared with `auto: true`, or when a person or a coordinating agent runs `harness lifecycle run deliver`. Most
runs need judgement first (which task commits go together, how to word them), so an agent arranges the commits with a
skill and then fires `deliver` ([ralph](ralph.md#delivery)).

### I run ralph-tui directly with my own config. Does `harness` interfere?

No. It never reads or writes a root `.ralph-tui/` unless you set `ralph.config: inherit`, and `ralph clean` touches only
sessions it recorded.

## Versions and updates

### A teammate's `harness` refuses our repository.

Their binary is older than the repository's `schema`. They need to update ([installation](installation.md#version-skew)).

### How do I turn off the update notice?

`export HARNESS_NO_UPDATE_CHECK=1`, or `session_start.update_notice: false` in `.agents/hooks.yaml` for agent sessions.

### Should we pin the version per repository?

Optional. A global install is enough because files carry a schema, not a version. Pin with
`mise use github:lmarqs/harness@<tag>` in the repository when CI or the team must run one exact version.
