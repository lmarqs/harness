# Configuration standards

Rules for files that people and agents edit and harness reads. Field-by-field reference lives in
[../features/](../features/) and in `harness explain profile|policy|hooks`.

## The user-editable files

| File | Scope | Holds |
| --- | --- | --- |
| `.agents/harness.yaml` | repository, committed | the profile: modules, front door, commands, lanes, tracker, agent roles, Ralph defaults, skill sources, `extends` |
| `.agents/policy.yaml` | repository, committed | permission entries added to the built-in base |
| `.agents/hooks.yaml` | repository, committed | agent-neutral hook declarations and lifecycle actions |
| `~/.config/harness/fleet.yaml` (or `--repos <file>`) | machine | `repos` (the repository list), `branch_prefix` (default `harness/`) and `commit_message` (default `chore(harness): <command>`) for `harness fleet` |

Each fact has one file ([principles.md](principles.md#6-one-way-to-do-each-thing)). A new setting goes in the file
whose audience it serves: permissions in the policy, hooks in the hooks file, everything else harness needs about the
repository in the profile. A fourth repository file requires a UX record.

## Ownership

- The three repository files belong to the repository. harness writes them only when seeding (`scaffold`,
  `scaffold --adopt`) and when migrating a schema. Each such write is shown before it happens, through `--dry-run`
  and the confirmation rules in [cli-standards.md](cli-standards.md#confirmations---yes-and---force).
- When harness writes one of these files, it preserves comments, key order and blank lines that it does not need to
  change.
- Every entry adoption imports carries a provenance comment naming its source file and scope
  (`# imported from .claude/settings.json (project)`). The comment belongs to the repository from then on: harness
  never rewrites or removes it, and a person MAY delete it once the entry is reviewed.
- Machine-level files (fleet file, cache under `~/.cache/harness/`) never affect what is rendered into a repository.

## Schema versioning

Every repository file starts with `schema: <integer>`. The schema is per file and changes only for a breaking change
to that file's format ([compatibility-standards.md](compatibility-standards.md#what-counts-as-breaking)).

| The file's schema is… | harness… |
| --- | --- |
| the current one | reads it |
| older and supported | reads it as written; rendering is unchanged; the migration is offered, never applied silently |
| newer than the binary knows | refuses, exit 3 (a person must update the binary), naming the file, the schema found, the highest schema supported and the command to update harness |
| missing or not an integer | refuses, exit 1, with the expected first line |

Migration UX:

- A migration is written only by a command that mutates the file, after printing the diff it will apply.
- The migrated file keeps every comment and every value whose meaning did not change.
- A migration is idempotent and never changes the rendered output by itself.

## Defaults

- An absent key takes its documented default. Defaults are listed in the features reference and in
  `harness explain`.
- A module absent from `modules` is off. Adoption is opt-in per module.
- Seeded files contain the values harness detected and the switches a user is expected to change, not a copy of every
  default.
- A default never weakens safety and never changes within a schema version.

## Additive evolution

New behaviour enters the files in this order of preference:

1. A new optional key whose absence keeps the existing behaviour. This is not a breaking change and does not bump the
   schema.
2. A new value in an existing enumerated key, under the same condition.
3. A renamed, removed or redefined key, or a changed default: a breaking change, a schema bump and a migration.

A file that uses a key newer than the installed binary fails validation on that binary rather than being rendered
with the key ignored: an older binary refuses, it does not render something different.

## Validation messages

Validation runs on every command that reads the file, and `harness doctor` reports it as findings. Every message:

- names the file, line and column, and the full key path (`verify.lanes.e2e.required_when[2]`);
- shows the offending value and what was expected (type, allowed values, pattern);
- for an unknown key, names the closest known key and adds that a newer harness may define it;
- ends with the command that confirms the fix.

All problems in a file are reported in one run.

```text
harness: .agents/harness.yaml:14:5: unknown key "verify.lanes.e2e.requried_when"
  did you mean "required_when"? if the key comes from a newer harness, update: mise use -g github:lmarqs/harness@<tag>
  then run: harness doctor
```

## Values and placeholders

- Paths and globs are relative to the repository root, use forward slashes, and support `**`.
- Commands are strings run with `sh -c` from the root of the checkout they apply to (the primary clone or a worktree).
- The placeholders `{file}` (one repository-relative path) and `{pattern}` (one lane pattern) in profile and hook
  commands are the only substitutions; harness shell-quotes each value. Lifecycle actions get values only through
  environment variables ([cli-standards.md](cli-standards.md#values-passed-to-user-declared-commands)). Each field
  documents which placeholder it accepts; any other placeholder is a validation error, and a new value is added as an
  environment variable, never as a new placeholder.
- Lists are YAML lists, never comma-separated strings.

## Policy entries

`.agents/policy.yaml` adds entries to the built-in base under `shared.allow`, `shared.ask`, `shared.deny` and
`worker.deny`. Every native rule an agent understands is expressible, in one of three forms:

| Form | Example | Renders as |
| --- | --- | --- |
| Prefix list (a nested list gives alternatives for one word) | `[git, push]`, `[mise, run, [lint, test]]` | a shell-command prefix for every agent: Claude `Bash(<words> *)`, Codex `prefix_rule` |
| Native rule string: `Tool(specifier)`, `Tool`, or `mcp__server__tool`, with globs | `WebFetch(domain:example.com)`, `Read(./secrets/**)`, `mcp__acme-observability__get_*` | verbatim for Claude. For Codex only `Bash(<prefix> *)` with a pure prefix converts; any other string is Claude-only |
| Bare string, without `Tool(` | `"gh pr merge *--admin*"` | shorthand for `Bash(<string>)`, Claude-only |

- MCP tools are native rule strings under `allow`, `ask` or `deny`, like any other tool.
- Precedence is the same for every agent: deny, then ask, then allow.
- A Claude-only entry is listed as such in the `policy-summary` block, so a reader sees which rules Codex does not
  enforce.
- The same pattern under `allow` and `deny` resolves to deny and is reported by `harness doctor`.
- An entry that would weaken a built-in invariant (allowing a permission-bypass flag, or a force push to the default
  branch) is a validation error.

## Lifecycle actions

Lifecycle actions are declared under the top-level `lifecycle:` key of `.agents/hooks.yaml`, beside the agent-hook keys
(`post_edit`, `validators`, `session_start`, `raw`), or in a preset. Behaviour, trigger points and guards:
[lifecycle guide](../features/lifecycle.md).

```yaml
lifecycle:
  deliver:
    - name: push
      run: git push -u origin "$HARNESS_BRANCH"
    - name: open-pr
      run: gh pr create --draft --fill --base "$HARNESS_DEFAULT_BRANCH" --head "$HARNESS_BRANCH"
      auto: false
      scope: [task, change]
      on_failure: stop
  landed:
    - name: delete-remote-branch
      run: git push origin --delete "$HARNESS_BRANCH"
      auto: true
```

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `lifecycle.<event>` | list | none | actions for one event: `start`, `verified`, `completed`, `deliver` or `landed`; any other key is a validation error |
| `name` | string | required | the action's name, unique within its event; `harness lifecycle run --action <name>` selects it |
| `run` | command | required | run with `sh -c` in the worktree; receives values only as `HARNESS_*` environment variables |
| `auto` | boolean | `false` | when true, harness fires the action at the event's trigger point; when false, it runs only through `harness lifecycle run` |
| `scope` | list of `task`, `run`, `change` | every scope the event supports | the scopes in which the action runs; a scope the event does not support is a validation error |
| `on_failure` | `stop` or `continue` | `stop` | `stop`: the first failure stops the event with exit 2; `continue`: the remaining actions run, and the event still exits 2 if any action failed |

- Actions run in declaration order: the preset's actions first, then the repository's. A repository action with the
  same name as a preset action replaces it in place.
- Actions are code. They are committed, reviewed like code, and run as child processes of harness with the
  permissions of the person or process that fired the event, outside any agent's per-command permission checks.
- `auto` defaults to false, so adding an action never makes anything leave the clone unattended; `auto: true` is a
  reviewed, committed decision.

## Presets

- `extends` names one source: a path, a git URL or `github:owner/repo//path`, optionally pinned with `@<ref>`
  (`github:acme/harness-presets//presets/api@v2`).
- A preset adds policy entries, hook declarations, lifecycle actions, block text and lane defaults.
- Precedence: repository entries are applied after preset entries. Lists (policy entries, hook entries, actions) are
  unions; a repository lane with the same name as a preset lane replaces it; a repository action with the same name
  as a preset action replaces it; nothing removes a built-in invariant.
- The manifest records the commit every preset resolved to. `harness render` and `harness render --check` reuse the
  recorded commit, so a change upstream never alters rendering or fails a check by itself. `harness render --refresh`
  re-resolves presets without `@<ref>`; `harness doctor` reports a newer upstream commit as a note.
- A preset that cannot be fetched and is not cached is an error naming the source; a cached copy is used offline.

## No secrets

- The three repository files and every preset are committed and readable by everyone with access to them. They MUST
  NOT contain tokens, passwords, keys or internal credentials, in values, comments or action commands.
- A setting or action that needs a credential refers to an environment variable by name, or relies on the
  authentication of the tool it calls (`git`, the hosting platform's CLI). Lifecycle actions inherit the environment of
  the process that fires them; that environment, never a file, carries the credential.
- harness never prints the value of an environment variable it reads. A lifecycle dry run prints only the `HARNESS_*`
  variables harness sets for the action.
- Personal preferences belong in the agents' own local files (`settings.local.json`, `*.local.toml`) or in `HARNESS_*`
  environment variables. harness never writes those files; they are read only by `harness scaffold --import local`,
  and what that import copies into `policy.yaml` becomes committed and shared, and is marked for review in the to-do.

## What never becomes a setting

The following are not keys in any file, preset or flag. They are architecture decisions: changing one requires an ADR,
not a UX record.

- Workers never bypass permissions.
- Force pushes to the detected default branch are denied.
- mise `:::` task chaining is denied.
- Proofs are replayed outside the agent's writable tree, and completion is withheld until the replay passes.
- Hooks never decide permissions.
- Tool-owned files are restored from the session's base commit before each worker launch and compared after it; any
  difference fails the attempt.
- Lifecycle actions never run inside a Ralph worker; `harness lifecycle` is in the built-in worker deny.
- `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
  without a valid receipt for the work's base.
- The binary ships no lifecycle actions and contains no hosting-platform logic.
- The binary never writes knowledge and never writes the team's record of work (issues, projects, boards, comments,
  reviews, labels).
- The binary never reconfigures state it did not create.
- The managed-block marker syntax, the generated-file header and the manifest location.
- Whether local override files (`settings.local.json`, `*.local.toml`) are written: they never are.

Background: the ADR on invariants in [../adrs/](../adrs/).
