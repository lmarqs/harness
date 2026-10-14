# Configuration standards

## Objective

**Problem.** Developers and coding agents edit a repository's tool configuration, reviewers approve changes to it, and
every teammate's machine and CI pipeline acts on it. When each new setting goes wherever its author chose, one fact sits
in two places that disagree, a reviewer has to search several files to see one permission change, and a mistyped key, or
a file written for a different release, is quietly ignored or misread and drops a rule someone declared. When a setting
can come from outside the repository, nobody reading the repository can tell what it does there, and a credential
written into a setting is published to everyone who can read the repository.

**Goal.** Each fact has one place in the repository's own files, anyone can tell from those files alone what the tool
does in that repository, and the files hold no credential. Every file is either read exactly as written or refused with
its mistake named, never partly applied.

## Overview

These rules cover the files that people and workers edit and `the-cook` reads. The field-by-field reference is in
[../features/](../features/) and in `the-cook explain profile|policy|hooks|lifecycle|context`. Terms used here in a
precise sense, such as profile, seeded, owned, receipt, worker, ingredient, station and recipe, are defined in the
[glossary](../features/glossary.md).

## Where files live

Two directories have two owners, and state lives outside both. Why: tool configuration that sits among the content of
`.agents/` makes removal pick files out of a tree others write, and state inside a committed tree needs ignore rules
inside it ([ADR 0014](../adrs/0014-directory-layout.md)).

| Location | Owner | Holds |
| --- | --- | --- |
| `.cook/` | the versioned configuration of `the-cook` | `config.yaml` (the profile), `policy.yaml`, `hooks.yaml`, `wrapper.yaml`, the manifest `manifest.json`, and the repository's recipes in `recipes/<name>/`. Everything in it is committed; nothing in it is state and nothing in it is ignored |
| `.agents/` | the content workers read, a convention of the `AGENTS.md` ecosystem that other tools, such as skills installers, share | `skills/` (including the tool-owned `skills/the-cook/`), `memory/`, `commands/`, `REVIEW.md`, and repository files that standing context points to. It stays meaningful without `the-cook` |
| repository root | the repository, with managed blocks | `cook` (the wrapper, tool-owned), `AGENTS.md`, `README.md`, and the files the ingredients in use render blocks into, such as a worker's instructions file and the version control system's ignore file |
| state paths ([below](#state-paths)) | `the-cook`, clone-local | receipts, sessions, locks, the lifecycle log, the hook cache, standing-context session records, the output and changes of the last failed attempt, managed workspaces |

- The configuration of `the-cook` never goes in `.agents/`, and the content of `.agents/` never goes in `.cook/`, so
  each directory can be read, reviewed and removed on its own terms.
- State never goes in `.cook/` or `.agents/`, because both are committed trees.
- Each convention a location carries (what it holds, where state lives) is also written in `AGENTS.md`, so it holds
  without `the-cook` ([generated-content-standards.md](generated-content-standards.md#conventions-in-blocks)).

## The user-editable files

| File | Scope | Holds |
| --- | --- | --- |
| `.cook/config.yaml` | repository, committed | the profile: the ingredients it pins and the stations it applied (`ingredients`, `stations`, `vcs`), modules, bootstrap, commands, lanes, workers (`workers.<name>`: ingredient, role, sandbox writable roots, model, effort), standing context, block budget, state paths |
| `.cook/policy.yaml` | repository, committed | the repository's permission entries; the floor of the ingredients in use is rendered beside them and is not in this file |
| `.cook/hooks.yaml` | repository, committed | hook declarations for worker events, written once for every worker; notification actions (`attention`, `turn_end`) and lifecycle actions |
| `.cook/wrapper.yaml` | repository, committed | the pinned release: `schema`, `version`, `source`, `checksums`; owned: written only by `the-cook scaffold` and `the-cook wrapper`, never by hand |
| `.cook/recipes/<name>/recipe.yaml` | repository, committed | one [recipe](#recipes): one kind of change the repository makes many times, with its template files in `files/` beside it |
| `~/.config/the-cook/fleet.yaml` (or `--repos <file>`) | machine | `repos` (the repository list), `branch_prefix` (default `the-cook/`) and `commit_message` (default `chore(the-cook): <command>`) for `the-cook fleet` |

Each fact has one file ([principles.md](principles.md#6-one-way-to-do-each-thing)). A new setting goes in the file
whose audience it serves: permissions in the policy, hooks and actions in the hooks file, the pinned version in the
wrapper file, each kind of change the repository makes many times in its own recipe, everything else `the-cook` needs
about the repository in the profile. Why: a reviewer of a permission change reads one file that holds only permissions.
Another repository file requires a change to this standard, with its reason. Recipes are the one kind of file there are
many of, one directory per recipe, because each is added, updated, run and deleted as a unit with its template files.

### `wrapper.yaml`

```yaml
# generated by the-cook: change with ./cook wrapper --version <tag>
schema: 1
version: <tag>
source: github:lmarqs/the-cook
checksums:
  linux-amd64: <sha256>
  linux-arm64: <sha256>
  darwin-amd64: <sha256>
  darwin-arm64: <sha256>
```

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | the file's schema |
| `version` | release tag | required | the exact release the wrapper runs; never a range or `latest` |
| `source` | source | `github:lmarqs/the-cook` | where releases come from: `github:<owner>/<repo>`, shorthand for `https://github.com/<owner>/<repo>/releases`, or any HTTPS base URL laid out the same way: assets at `<source>/download/<tag>/<asset>`, the newest release's files at `<source>/latest/download/<asset>` |
| `checksums` | map of platform to sha256 | required | the checksum of the release asset for each supported platform: `linux-amd64`, `linux-arm64`, `darwin-amd64`, `darwin-arm64`; the wrapper refuses a download that does not match |

- `version` is exact, never a range, because a range would let two machines run different releases against one
  commit.
- `source` is a location of plain files over HTTPS, never a platform API, so a mirror is a copy of the release files
  and works for networks that cannot reach the project's release host
  ([ADR 0017](../adrs/0017-engine-version.md)).
- `wrapper.yaml` is owned: it starts with the header above, the manifest records it, and a hand edit is drift. Why:
  its checksums are worth something only if a trusted binary verified them, so a hand edit must not pass as the
  repository's choice ([ADR 0017](../adrs/0017-engine-version.md)).
- `wrapper.yaml` is the only place `the-cook` reads the version it runs, so the wrapper and a global `the-cook` resolve
  the same release from one file. `the-cook` reads no other tool's version file
  ([ADR 0022](../adrs/0022-tool-integration.md)).
- `the-cook wrapper --version <tag>` (or `--latest`) and `the-cook scaffold` write `checksums` only from the release's
  checksum file after the running binary has verified its signature, or, with `scaffold --offline`, write the literal
  `pending`, which admits no download
  ([compatibility-standards.md](compatibility-standards.md#release-trust)), so a person never copies a checksum by
  hand and a forged release is refused before anything is written. The change reaches review as a diff.
- `wrapper.yaml` is part of the public contract ([compatibility-standards.md](compatibility-standards.md#versioning)).

### State paths

State lives at paths the profile sets, outside `.cook/`, because repositories already have conventions for where
local state goes, and a tool that fixes one location fights them:

```yaml
state:
  dir: .work
workspaces:
  dir: .work/workspaces
```

| Key | Default | Holds |
| --- | --- | --- |
| `state.dir` | `.work` | receipts, sessions, locks, the lifecycle log, the hook cache, standing-context session records; in each checkout an attempt runs in, the record of its bootstrap, `last-attempt-output` and `last-attempt.patch` |
| `workspaces.dir` | `.work/workspaces` | each managed workspace, at `<dir>/<name>` |

- Paths are repository-relative. A path inside `.cook/` or `.agents/` is a validation error (exit 1), because both
  trees are committed.
- Defaults are literal paths. Changing one re-renders the blocks that state it and does not move existing state,
  because moving a workspace with uncommitted work is a person's decision.
- Each path is a convention: the module that uses it states it in a managed block of `AGENTS.md`, and the
  version-control ingredient ignores it in a managed block of its ignore file, naming the configured value
  ([generated-content-standards.md](generated-content-standards.md#conventions-in-blocks)).
- Other programs keep their files where they look for them, such as a task tracker's database or a loop runner's
  configuration directory. No profile key names those paths: the ingredient that describes a program renders the ignore
  entries they need, such as a block in the ignore file, because the binary contains code and data for no program
  ([ADR 0022](../adrs/0022-tool-integration.md)).
- `workspaces` holds one key that is not a path: `update_before_landed`, a boolean, default `false`. When `true`,
  `the-cook workspace remove` runs the version-control contract's `update` before it detects landed work; when
  `update` fails, as it does offline, or the ingredient has none, it prints one note and detects against what the
  checkout knows. `the-cook make`, `the-cook workspace create` and `the-cook doctor` never run `update`. Why: learning
  what is shared asks the version control system to reach beyond the clone, which no command does unless the
  repository asks, and whether work lands somewhere else, such as through a hosting platform's merge, is the
  repository's practice, which a station may seed as an entry ([profile](../features/profile.md#workspaces)).

## Ownership

- The repository files `config.yaml`, `policy.yaml` and `hooks.yaml` are seeded: `the-cook` writes them once at
  `scaffold` or `scaffold --adopt`, and from then on they belong to the repository. `the-cook` writes them again only to
  migrate a schema, to pin an ingredient or to apply a station a person asked for ([below](#ingredients-and-stations)).
  `wrapper.yaml` and `manifest.json` are owned. `the-cook` writes `wrapper.yaml` on scaffold and re-pin
  (`the-cook wrapper`), and `manifest.json` on scaffold, render and re-pin. `wrapper.yaml` carries a header naming the
  operation that changes it; `manifest.json`, being JSON, carries none. Each such write is shown before it happens,
  through `--dry-run` and the confirmation rules in
  [cli-standards.md](cli-standards.md#confirmations---yes-and---force). Why: the seeded files hold the repository's
  choices, so `the-cook` must not overwrite them; the owned files hold facts `the-cook` computes.
- A recipe in `.cook/recipes/<name>/` belongs to the repository from the moment it is written. `the-cook` writes one
  only when a person adds, updates or creates it (`the-cook recipe add`, `recipe update`, `recipe create`) or applies a
  station that adds it where its directory is absent, and every write but `recipe create`'s skeleton is shown as a diff
  and confirmed first. `the-cook remove` deletes `.cook/recipes/` with the seeded files in `.cook/`, after listing each
  recipe in its plan, so a person can keep a copy first. Why: a recipe is the repository's own description of its work,
  which `the-cook` must not change unasked, and it means nothing without `the-cook`, so it does not outlive it.
- When `the-cook` writes one of these files, it preserves comments, key order and blank lines that it does not need to
  change, because people annotate them.
- Every entry adoption imports carries a provenance comment naming its source file and scope
  (`# imported from <file> (project)`). The comment belongs to the repository from then on: `the-cook`
  never rewrites or removes it, and a person MAY delete it once the entry is reviewed. Undo uses project-scope
  provenance comments to write those entries back as native settings.

## Inputs

What `the-cook` renders and runs in a repository is determined by the repository's own files, the release it pins and
the ingredients it pins by hash, and by nothing else ([ADR 0013](../adrs/0013-organization-content.md),
[ADR 0022](../adrs/0022-tool-integration.md)).

- No repository file has a key that names a source outside the repository for what `the-cook` renders or runs,
  whether by inheritance, an include or a remote source. Validation rejects such a key like any other unknown key.
  Why: people and workers learn what a repository does from its files. A rule resolved from elsewhere acts in the
  repository while its source sits outside it, so a worker in the repository cannot read it and a reviewer sees its
  effect without its reason. With the repository's files and the content they pin by hash as the only inputs, the
  same commit renders the same bytes on every machine and every day. Rendering fetches nothing and caches nothing, and
  no check fails because someone else published a change.
- Three keys name a source outside the repository, and each is pinned by hash. `source` in `wrapper.yaml` is where the
  wrapper downloads the pinned release, which it runs only when the download matches the committed checksums. `source`
  of each entry under `ingredients:` and under `stations:` is the cookbook the ingredient or station is downloaded from,
  by the commands that pin, apply, fetch or read it, and `the-cook` loads either only from the per-user cache and only
  when its archive matches the committed `sha256`. Why: what arrives from any of them takes effect only as the content
  the repository pinned and reviewed. Shared skills arrive the same way as any other file: the team installs them with
  the tool of its choice and commits `.agents/skills/`.
- A recipe's `origin` records the cookbook, tag and sha256 a copied recipe came from, and names no source for what
  runs: `the-cook make` runs the copy in `.cook/recipes/`, and only `the-cook recipe update` and `the-cook fetch` read
  `origin`, to merge a newer version into the copy. Why: a copy the repository owns is read and reviewed where it acts,
  while a recipe resolved from its cookbook at run time would act from outside the repository.
- Machine-level files (the fleet file, the cache under `$COOK_HOME`) never affect what is rendered into a
  repository: a cached ingredient or station is loaded only when it matches the hash the repository pins, so two
  machines render the same bytes.
- An organization's choices (which observability tools coding agents may read without asking, a deny on each
  repository's deploy command, a CI-trigger commit in delivery
  ([example](../features/lifecycle.md#an-organization-convention-a-ci-trigger-commit-before-the-pull-request)), the
  actions that keep the task tracker its repositories use up to date) are entries in each repository's files, written by
  a person or by a coding agent following a skill the organization publishes, and reviewed in each repository as a diff
  ([when-cli-when-skill.md](when-cli-when-skill.md)). Why: those choices differ between an organization's repositories,
  so applying them takes judgement, and afterwards each choice is in the files of the repository it governs. The cost is
  that a choice shared by many repositories is written, and changed, in each of them.

## Schema versioning

Every repository file starts with `schema: <integer>`. The schema is per file and changes only for a breaking change
to that file's format ([compatibility-standards.md](compatibility-standards.md#what-counts-as-breaking)). Why: a binary
must recognise a file it does not understand before it rewrites that file with outdated rules
([ADR 0005](../adrs/0005-schema-compatibility.md)).

| The file's schema is… | `the-cook`… |
| --- | --- |
| the current one | reads it |
| older and supported | reads it as written; rendering is unchanged; the migration is offered, never applied silently |
| newer than the binary knows | refuses, exit 3 (a person must decide which version to run), naming the file, the schema found, the highest schema this binary supports, and the release to run: the pinned release from `.cook/wrapper.yaml`, which `./cook <command>` runs ([ADR 0005](../adrs/0005-schema-compatibility.md)) |
| missing or not an integer | refuses, exit 1, with the expected first line |

Every machine running through the wrapper runs the pinned version, so a newer-schema refusal occurs only when the pin is
bypassed (`COOK_BINARY`, or a global binary too old to defer to the pin)
([compatibility-standards.md](compatibility-standards.md#installation-and-version-pinning)).

Migration UX:

- A migration is written only by a command that mutates the file, after printing the diff it will apply, because a
  migration rewrites a file the repository owns.
- The migrated file keeps every comment and every value whose meaning did not change.
- A migration is idempotent and never changes the rendered output by itself, so it can be reviewed apart from any
  behaviour change.

## Defaults

- An absent key takes its documented default. Defaults are listed in the features reference and in
  `the-cook explain`, so a short file is still fully specified.
- A module absent from `modules` is off. Adoption is opt-in per module, because a repository adopts the practices it
  chooses ([principles.md](principles.md#1-optional-and-transparent)).
- Seeded files contain the values `the-cook` detected and the switches a user is expected to change, not a copy of every
  default, so the file shows the repository's choices rather than the defaults of `the-cook`.
- A default never weakens safety and never changes within a schema version, because a default is applied to every
  repository that did not choose otherwise.

## Additive evolution

New behaviour enters the files in this order of preference, because each step costs repositories more than the one
before:

1. Data in the existing formats: an ingredient, a station, a recipe or a declared action. Support for any program takes
   the form of an ingredient or a declared action, what a way of working needs in place the form of a station, and a
   kind of change a repository makes many times the form of a recipe. It changes no schema of the repository files,
   and a repository that does not use the program or the practice carries nothing.
2. A new optional key whose absence keeps the existing behaviour. This is not a breaking change and does not bump the
   schema.
3. A new value in an existing enumerated key, under the same condition.
4. A renamed, removed or redefined key, or a changed default: a breaking change, a schema bump and a migration.

Code in the binary for a program is not on this list: a key, a value or a command that exists to serve one version
control system, worker, loop runner, tracker, skills installer, task runner or hosting platform is never added. A need
the ingredient format cannot express is a change to the ingredient schema or to a contract
([ingredients](../features/ingredients.md)). Why: each program has its own formats and releases, so code for one follows
every release of a program the project does not own and serves no team that chose another
([ADR 0022](../adrs/0022-tool-integration.md), [ADR 0023](../adrs/0023-core-and-contracts.md)).

A file that uses a key newer than the running binary fails validation: that binary refuses the file rather than
rendering it with the key ignored. Why: an ignored key would silently drop a permission, an action or a guard the
repository declared.

## Validation messages

Validation runs on every command that reads the file, and `the-cook doctor` reports it as findings. Every message:

- names the file, line and column, and the full key path (`verify.lanes.e2e.required_when[2]`);
- shows the offending value and what was expected (type, allowed values, pattern);
- for an unknown key, names the closest known key and adds that a newer release of `the-cook` may define it;
- ends with the command that confirms the fix.

All problems in a file are reported in one run. Why: the person or worker fixing the file needs the exact place and the
exact fix, and one round trip per mistake wastes both.

```text
the-cook: .cook/config.yaml:14:5: unknown key "verify.lanes.e2e.requried_when"
  did you mean "required_when"?
  if the key comes from a newer the-cook, raise the pin: ./cook wrapper --version <tag>
  then run: ./cook doctor
```

## Values and placeholders

- Paths and globs are relative to the repository root, use forward slashes, and support `**`, so one file works on
  every machine.
- Commands are strings run with `sh -c` from the root of the checkout they apply to (the primary clone or a workspace).
- The placeholders `{file}` (one repository-relative path) and `{pattern}` (one lane pattern) in profile and hook
  commands are the only substitutions in command text; `the-cook` shell-quotes each value. Each field documents which
  placeholder it accepts; any other placeholder is a validation error, and a new value is added as an environment
  variable, never as a new placeholder. Why: substitution into command text is an injection path, so it stays limited
  to two quoted values.
- A recipe's `{<input>}` and `{task}` placeholders are substituted only into argument lists that no shell parses, into
  paths and into plain text ([below](#recipes)), never into a command string, so the rule above holds for every string
  a shell reads.
- Actions receive values per kind
  ([cli-standards.md](cli-standards.md#values-passed-to-user-declared-commands-and-actions)):
  - `run`: only through environment variables, which the shell expands; `the-cook` substitutes nothing.
  - `http`: in `json`, every `$NAME` inside a string becomes the JSON-encoded value of environment variable `NAME`,
    so a value can never break the document's structure; an unset variable becomes an empty string and a note.
  - `attempt`: in `prompt`, every `$NAME` becomes the variable's value as plain text.
- Lists are YAML lists, never comma-separated strings, because values may contain commas.
- A string that holds `: ` or ` #`, or that holds `{`, `}`, `[`, `]` or `,` inside a flow collection (`{ ... }` or
  `[ ... ]`), is quoted in every file `the-cook` writes and every example its documentation shows. Why: YAML stops a
  plain value there. `unit: { select: npm test -- {pattern} }` does not parse, and `run: git commit -m "release #12"`
  parses as `git commit -m "release`, because ` #` starts a comment.

## Policy entries

`.cook/policy.yaml` holds the repository's permission entries under `shared.allow`, `shared.ask`, `shared.deny` and
`unattended.deny`; the floor of the ingredients in use is rendered beside them ([below](#the-floor)). Every native rule
a worker understands is expressible, in one of three forms, because a rule the policy cannot express is lost when
`the-cook` takes ownership of the native key ([ADR 0016](../adrs/0016-permission-adoption.md)):

| Form | Example | Renders as |
| --- | --- | --- |
| Prefix list (a nested list gives alternatives for one word) | `[git, push]`, `[make, [lint, test]]` | a shell-command prefix for every worker whose ingredient renders permissions, in the form that ingredient renders |
| Native rule string: a string that matches a native rule pattern of a declared worker's ingredient | `WebFetch(domain:example.com)`, `Read(./secrets/**)`, `mcp__acme_observability__get_*`, for a worker whose ingredient declares patterns for its program's tool rules | for each worker whose ingredient has a pattern the string matches, verbatim or converted into the program's own form, as the ingredient's renders say; for no other worker |
| Bare string: a string that matches no native rule pattern of any declared worker's ingredient | `"./deploy.sh *--force*"` | a whole-command glob rule, for each worker whose program matches whole shell commands by glob, as its ingredient's renders say; for no other worker |

- An entry that names a `the-cook` command is written once with the program name, `[the-cook, lifecycle, run, deliver]`,
  and rendered for every rendered spelling: `the-cook …`, `./cook …` and `cook …`. Why: an entry written for one
  spelling leaves the others open, and writing all of them by hand drifts. Other spellings (an absolute path to the
  binary, `sh cook …`) match no prefix rule; the binary's own guards catch them
  ([ADR 0011](../adrs/0011-safety-invariants.md)).
- A tool other than the shell, such as an MCP tool, gets a native rule string under `allow`, `ask` or `deny`, in the
  syntax of a worker whose ingredient declares patterns for it.
- Precedence is the same for every worker: deny, then ask, then allow, so one policy means the same thing in each.
- An entry that some worker's ingredient cannot render is listed in the `policy` block of `AGENTS.md` with the workers
  it does not reach, so a reader sees which rules each worker does not enforce.
- The same pattern under `allow` and `deny` resolves to deny and is reported by `the-cook doctor`, because the safer
  reading holds until a person decides.
- The worker ingredients, never the binary, decide which strings are native rule strings. Each worker ingredient
  declares, in `native_rules.patterns`, regular expressions in RE2 syntax, each matched against the whole string. A
  string is a native rule string for every declared worker whose ingredient has a pattern it matches, and a bare string
  when it matches no pattern of any declared worker's ingredient; a string that matches one is never a bare string, for
  any worker. Why: the binary knows no program's rule syntax, so only the ingredient that describes a program can
  recognise that program's rules. Declaring or removing a worker can change a string's form, and the `policy` block of
  `AGENTS.md` shows the result.
- An entry that would weaken a floor rule of an ingredient in use (allowing a bypass argument a worker ingredient marks,
  or a force push to the default branch a version-control ingredient denies) is never rendered: `the-cook render` drops
  it and reports it, the floor rule is rendered regardless, and `the-cook doctor` reports the entry as a finding. Why:
  floor rules hold in every repository that uses the ingredient, and a policy file that claims otherwise misleads its
  readers until someone removes the entry ([ADR 0011](../adrs/0011-safety-invariants.md),
  [ADR 0016](../adrs/0016-permission-adoption.md)).

## Declared actions

Actions are declared in `.cook/hooks.yaml`: lifecycle actions under the top-level `lifecycle:` key, notification
actions under `attention:` and `turn_end:`, beside the keys for hooks on worker events (`post_edit`, `validators`,
`session_start`, `raw`). Behaviour, trigger points and guards: [lifecycle guide](../features/lifecycle.md) and
[hooks](../features/hooks.md). Why: everything that leaves the clone is written down and reviewed before it can happen
([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)), in the repository where it runs
([ADR 0013](../adrs/0013-organization-content.md)).

```yaml
lifecycle:
  completed:
    - name: notify-chat
      http:
        url_env: CHAT_WEBHOOK_URL
        json: { text: "$COOK_TASK finished on $COOK_BRANCH: $COOK_SUMMARY" }
      auto: true
      on_failure: continue
  deliver:
    - name: push
      run: git push -u origin "$COOK_BRANCH"
    - name: open-pr
      run: gh pr create --draft --fill --base "$COOK_DEFAULT_BRANCH" --head "$COOK_BRANCH"
      scope: [task, change]
  landed:
    - name: lessons
      attempt:
        prompt: "Read the merged change on $COOK_BRANCH and add durable lessons to .agents/memory/."
  blocked:
    - name: page-me
      http:
        url_env: CHAT_WEBHOOK_URL
        json: { text: "the-cook stopped in $COOK_REPO: $COOK_REASON" }
      auto: true
attention:
  - name: ping-me
    http:
      url_env: CHAT_WEBHOOK_URL
      json: { text: "$COOK_WORKER needs you in $COOK_REPO: $COOK_MESSAGE" }
turn_end:
  - name: done
    run: notify-send "turn finished"
```

Every action has `name` and exactly one kind key, `run`, `http` or `attempt`, because an action with two kinds has no
single meaning.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `lifecycle.<event>` | list | none | actions for one event: `start`, `verified`, `completed`, `deliver`, `landed` or `blocked`; any other key is a validation error |
| `attention`, `turn_end` | list | none | notification actions: the worker is waiting for a person; the worker finished a turn. Kinds `run` and `http` only |
| `name` | string | required | the action's name, unique within its event; `the-cook lifecycle run --action <name>` selects a lifecycle action |
| `run` | command | one kind required | run with `sh -c` in the workspace; receives values only as environment variables |
| `http` | map | one kind required | a request `the-cook` performs itself, keys below |
| `attempt` | map | one kind required | a worker started unattended on a prompt, under the attempt contract, keys below; lifecycle actions only |
| `auto` | boolean | `false` | lifecycle actions: when true, `the-cook` fires the action at the event's trigger point; when false, it runs only through `the-cook lifecycle run` |
| `scope` | list of `task`, `run`, `change` | every scope the event supports | lifecycle actions: the scopes in which the action runs; a scope the event does not support is a validation error |
| `on_failure` | `stop` or `continue` | `stop` | lifecycle actions: `stop`: the first failure stops the event with exit 2; `continue`: the remaining actions run, and the event still exits 2 if any action failed |

`http` keys:

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `url_env` | environment variable name | required | the variable that holds the URL |
| `method` | HTTP method | `POST` | the request method |
| `headers_env` | map of header name to environment variable name | none | each header's value is read from its variable |
| `json` | any YAML value | none | the request body, sent as JSON, with `$NAME` expanded inside strings |
| `timeout` | duration | `10s` | the request timeout |

- Success is any 2xx status. Redirects are not followed across hosts, so a redirect cannot carry headers to another
  service.
- The URL and header values come only from environment variables, never from literals in the file, and are never
  logged, because they are usually secrets and the file is committed.

`attempt` keys:

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `prompt` | string | required | the prompt, with `$NAME` expanded as plain text |
| `worker` | worker name | the only worker declared with role `unattended`; required when there is not exactly one | the worker to start unattended |
| `model` | model id | the worker's default | the model to run, for a worker whose ingredient maps one; a worker with no model ignores it |
| `effort` | effort level | the worker's default | the reasoning effort, under the same condition |

- An `attempt` action runs under the attempt contract: the unattended arguments of the worker's ingredient, the floor of
  the ingredients in use and the repository's `unattended.deny` entries, floor bypass arguments refused, the tamper
  check. It cannot commit, push or fire lifecycle events; its file changes stay uncommitted and are listed in the
  lifecycle log and the output. Why: a worker started by an event is as unattended as one `the-cook attempt` starts, so
  it gets the same bounds.
- Actions run in the workspace of the work the event concerns. On `landed`, `run` and `http` actions run in the primary
  checkout. An `attempt` action runs in a fresh workspace at the revision of the shared default line of work, which
  `the-cook` creates under `workspaces.dir` as `landed-<name>-<n>` and records. `the-cook` leaves that workspace with
  the worker's uncommitted edits for a person to review, commit or remove with `the-cook workspace remove`. Why: once
  work has landed its workspace is about to go, and the primary checkout holds whatever a person is doing there
  ([ADR 0019](../adrs/0019-action-kinds.md)).
- Prompts that include untrusted values (task titles, worker messages) are a prompt-injection surface; the attempt
  contract bounds what an injected instruction can do.
- `auto: true` on an `attempt` action is allowed and is reported by `the-cook doctor` as a note, because each automatic
  run has the worker's cost, such as a coding agent's tokens, and may give a different result each time.
- An event's actions run in the order `hooks.yaml` declares them, so the order a reviewer reads is the order they
  run.
- Actions are code. They are committed, reviewed like code, and run as child processes of `the-cook` with the
  permissions of the person or process that fired the event, outside any worker's per-command permission checks.
- `auto` defaults to false, so adding an action never makes anything leave the clone unattended; `auto: true` is a
  reviewed, committed decision.
- `the-cook` is never an MCP client. Work that needs an MCP tool is an `attempt` action or the coordinating worker's
  job; a deterministic integration uses the service's API (`http`) or CLI (`run`). Why: the worker owns its MCP
  connections, their credentials and their permissions.

## Standing context

Standing context puts known guidance in front of a worker at the moment it applies, without anyone invoking it. It is
declared under `context:` in `.cook/config.yaml`. Reference: [context](../features/context.md). Why: guidance a
person must remember to invoke is skipped exactly when it matters, while a deterministic trigger applies it every time
([ADR 0020](../adrs/0020-standing-context.md)).

```yaml
context:
  always:
    - skill: coding-guidelines
    - file: .agents/directives/unattended-rules.md
      for: [unattended]
    - file: .agents/directives/house-rules.md
      static: true
  when:
    - paths: ["**/*.sql", "src/migration/**"]
      skill: sql-quality-check
    - prompt: [timezone, DST, "time zone"]
      skill: timezone-handling
      inject: pointer
  budget: 120
```

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `context.always` | list of entries | none | injected at every session start, and again where the worker re-fires session start after compaction |
| `context.when` | list of entries, each with `paths` or `prompt` | none | `paths`: injected after the worker first reads or edits a matching file in the session; `prompt`: injected when the person's message contains one of the words or phrases (case-insensitive, whole words) |
| `skill` | installed skill name | one of `skill`, `file` | the body of `.agents/skills/<name>/SKILL.md`, front matter stripped |
| `file` | repository path | one of `skill`, `file` | a repository file |
| `for` | list of `interactive`, `unattended` | both | the worker roles that receive the entry |
| `inject` | `content` or `pointer` | `content` for `always`, `pointer` for `when` | the text itself, or one line: "Apply the <name> skill now: read <path>" |
| `static` | boolean | `false` | `always` entries only: the entry is written into the `context` convention block of `AGENTS.md` instead of injected by a hook |
| `context.budget` | integer | `120` | the maximum lines of always-on content per session per role |

- Each `when` entry is injected at most once per session, so a matching file read twenty times costs one injection.
- Sources are repository files and skills committed under `.agents/skills/` only, delivered as the committed file. A
  skill named in `context` that does not exist under `.agents/skills/` is a `the-cook doctor` finding. Nothing is
  fetched when a hook runs, and no installer's lock file is read. Why: injected text steers a worker, so it must come
  from reviewed, versioned content, and the commit is that review whichever tool installed the skill
  ([ADR 0022](../adrs/0022-tool-integration.md)).
- For a worker in an attempt, injected content is read from the base commit the attempt recorded, so a worker cannot
  change its own instructions between attempts.
- In every attempt whose prompt arrives on stdin, `the-cook attempt`, an `attempt` action or a recipe's `attempt` step,
  the binary puts the `always` entries of the `unattended` role before the task text, each headed by its naming line,
  and the session-start hook of that attempt injects no `always` entry. A prompt the caller passes in the worker's
  arguments is left as it is, and the session-start hook delivers the entries. `when` entries are always
  hook-delivered. `context.budget` and `COOK_NO_CONTEXT=1` apply to prompt delivery as to hooks. Why: a worker program
  may run no hook unattended, since some run a project's hooks only after a person reviewed them, and nobody reviews
  in an attempt; the binary can place text only in a prompt that passes through it
  ([context](../features/context.md#delivery-in-the-prompt), [ADR 0020](../adrs/0020-standing-context.md)).
- A `static` entry works without the binary and survives eject, because it is a convention block; it is not also
  injected by a hook, so it is never loaded twice. Static entries count against `context.budget`, not against the
  `AGENTS.md` block budget.
- `the-cook doctor` reports a note when always-on content for a role exceeds `context.budget` and lists every entry
  with its line count, because every always-on line is paid in every session.
- `the-cook context show` previews what would be injected and why, so the effect of an entry can be checked without
  starting a worker.

## Ingredients and stations

`the-cook` is a declared software development lifecycle that knows no program: a stage that needs one reaches it through
a contract an ingredient fills, or through an action the repository declares ([lifecycle](../features/lifecycle.md)). A
cookbook publishes three kinds of unit. An **ingredient** is a named, versioned description of one program: the version
control system, a worker such as a coding agent, a loop runner, a task tracker, a task runner or a skills installer
([ingredients](../features/ingredients.md)). A **station** is what one way of working needs in place: the ingredients
to pin, the workers and their roles, entries for the repository's files, templates and the recipes to add, such as
agentic coding, or the commands and lanes of one stack ([stations](../features/stations.md)). A **recipe** is one kind
of change, made many times ([below](#recipes)). The binary contains code and data for no program and ships no
ingredient, no station and no recipe; every one comes from a cookbook, or, for a recipe, from the repository itself.
This section governs how a repository pins ingredients and applies stations, and what each part may write.
Why: each team picks different programs and each program changes on its own schedule, so a program's description changes
with the program and not with the binary, while each repository keeps the final say over its own files
([ADR 0022](../adrs/0022-tool-integration.md), [ADR 0023](../adrs/0023-core-and-contracts.md),
[ADR 0013](../adrs/0013-organization-content.md)).

One rule separates the three: facts about programs are pinned and rendered; practices are copied and become the
repository's own. What a repository needs from a program is rendered every time, what it needs from a way of working is
applied once, and a kind of change is copied once and then run many times. So an ingredient holds only what is rendered
or run, a station only what is applied, and a recipe only the steps of one kind of change. Why: in one unit, the part
the repository owns and the part `the-cook` renders would share one pin, so moving or deleting that pin would mean
something different for each part ([ADR 0024](../adrs/0024-ingredients-and-stations.md)).

### The ingredients and stations in use

The profile pins every ingredient the repository uses, records every station it applied, and names the ingredient that
fills the version-control contract and the ingredient of each worker:

```yaml
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 5b1e4c0a9d8f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4
  - name: acme-deploy
    source: https://cookbook.acme.example
    version: v1.4.0
    sha256: 9c3f2e1d0b8a7f6e5d4c3b2a19f8e7d6c5b4a39281f0e9d8c7b6a5f4e3d2c1b0
stations:
  - name: agentic-coding
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 0b7e2f4a6c8d1e3f5a7b9c0d2e4f6a8b1c3d5e7f9a0b2c4d6e8f1a3b5c7d9e0f
vcs: git
workers:
  reviewer: { ingredient: claude, role: interactive }
```

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `ingredients` | list | empty; `the-cook scaffold --cookbook` writes the ingredients its stations list | every ingredient the repository's renders and runs depend on, each a map with `name`, `source`, `version` and `sha256` |
| `ingredients[].name` | ingredient name | required | the ingredient's name, equal to the `name` in its `ingredient.yaml` and unique within `ingredients` |
| `ingredients[].source` | source | required | the cookbook that publishes the ingredient: `github:<owner>/<repo>`, or any HTTPS base URL laid out like the release source in `wrapper.yaml` |
| `ingredients[].version` | exact version | required | the cookbook tag; never a range |
| `ingredients[].sha256` | sha256 | required | the sha256 of the ingredient's archive, as the tag's `checksums.txt` states it; `the-cook` loads the ingredient only when its archive matches |
| `stations` | list | empty; `the-cook scaffold --cookbook` writes the stations it applies | every station applied to the repository, with the pin it was applied at: `name` (equal to the `name` in its `station.yaml`, unique within `stations`), `source`, `version` and `sha256`, as under `ingredients` |
| `vcs` | ingredient name | none; applying a station writes the one its stages name | the ingredient that fills the [version-control contract](../features/ingredients.md#the-version-control-contract) |
| `workers.<name>` | map | none; applying a station adds the workers its stages declare | a worker the repository uses, with `ingredient`, `role`, `writable_roots`, `network`, `jail`, `model` and `effort` ([workers](../features/workers.md)) |
| `workers.<name>.ingredient` | ingredient name | the worker's name | the ingredient that fills the [worker contract](../features/ingredients.md#the-worker-contract) for that worker |
| `workers.<name>.network` | boolean | `false` | whether the worker's attempts may use the network; `true` passes the ingredient's `launch.network` arguments at every unattended launch |
| `workers.<name>.jail.ingredient` | ingredient name | none; required with `jail` | the ingredient that fills the [jail contract](../features/ingredients.md#the-jail-contract) around the worker's attempts |
| `workers.<name>.jail.args` | list of arguments | `[]` | the jail's options this repository chooses, placed before the jail's separator |

- `vcs`, every `workers.<name>.ingredient` and every `workers.<name>.jail.ingredient` name an ingredient listed in
  `ingredients` that fills the matching contract, the version-control contract for `vcs`, the worker contract for a
  worker and the jail contract for a jail; any other name is a validation error (exit 1). An ingredient fills at most
  one contract. Why: an answer the binary relies on, such as the base revision of an attempt, has exactly one source,
  and a name that resolves to nothing must fail before anything runs.
- `workers.<name>.network: true` on a worker whose ingredient has no `launch.network` is a validation error (exit 1),
  as `writable_roots` is without `launch.sandbox_roots`; `false` passes nothing about the network, so the program's own
  default holds, which the ingredient's page in its cookbook states. Why: whether attempts may reach the network is the
  repository's practice and the arguments that carry it are the program's fact, so the value is the profile's and its
  mapping the ingredient's; a `true` the program never receives would let a reviewer believe the attempts reach the
  network when nothing told the program ([workers](../features/workers.md#declaring-workers)).
- `workers.<name>.jail` starts every attempt of that worker, and the replay of its work, inside the jail; it never
  applies to an interactive session. The jail's ingredient holds the arguments that carry each share, variable and the
  network; `jail.args` holds the options the repository chooses, such as how the worker program's login reaches the
  jail. A worker with a jail fails validation (exit 1) when its jail ingredient lacks `share.read` or `share.write`,
  declares neither or both of `env.pass` and `env.inherited`, or has no `network` for a worker with `network: true`;
  when `jail.args` holds a floor bypass argument of the jail; when a `writable_roots` entry contains the
  version-control metadata or the per-user cache; and when the version-control ingredient has no `metadata`. Why: the
  choice to confine and the options are the repository's practice, while the arguments are the jail program's fact, so
  the value is the profile's and the arguments the pinned ingredient's; a free-form command prefix would leave the
  binary unable to place the worker's launch, tell the jail's arguments from the worker's when it refuses bypass
  arguments, or pass the attempt marks, and every repository would retype the jail's facts
  ([workers](../features/workers.md#jails), [ADR 0027](../adrs/0027-worker-jails.md)).
- Names are unique within each list: two `ingredients` entries, or two `stations` entries, with the same `name` are a
  validation error (exit 1). Why: every reference to an ingredient, from `vcs`, a worker or the manifest, must resolve
  to exactly one, and applying a station again must resolve to one pin.
- `ingredients` is what renders and runs depend on. `stations` records which stations were applied and at which pin, for
  provenance and for applying a newer version later; a station in it has no effect on what is rendered, and deleting it
  changes nothing rendered. Why: what a station brings becomes the repository's own when it is applied, so nothing is
  left for its record to control.
- `the-cook ingredient pin` writes ingredient entries, and `the-cook station apply --cookbook` writes a station's entry
  together with the ingredient entries the station lists, at the sha256 the station lists for each. Each `sha256` comes
  from the tag's `checksums.txt`, downloaded over HTTPS, or from a station that file verified, and every entry is shown
  in one diff before anything is written. An ingredient the repository already pins at another sha256 keeps the
  repository's pin, and the difference is reported. Why: the repository's pin is a choice it reviewed, and applying a
  station only adds.
- An archive is downloaded only by the commands that pin, apply, fetch or read a cookbook tag, cached in the per-user
  cache under its hash (`$COOK_HOME/cookbook/<sha256>.tar.gz`), and verified against its `sha256` on every download and
  every load; a mismatch exits 1 and the archive is not used. Why: the reviewed pin is the trust decision, and no
  release key vouches for an ingredient or a station, so the hash keeps every later load equal to what the reviewer read
  ([ingredients](../features/ingredients.md#trust)).
- Hooks and unattended attempts never download. A pinned ingredient that is not in the cache when a command needs it,
  `the-cook render` or `the-cook attempt`, is a missing prerequisite, exit 3, and the message names `./cook fetch`; a
  hook prints one stderr line and exits 0. A command that needs the version-control contract, or the worker contract for
  a worker, when no pinned ingredient fills it exits 3 and names `./cook ingredient pin`. Why: a fetch during an
  unattended attempt would hand the attempt to whoever controls the source, and a render that fetched would depend on
  the network ([ADR 0022](../adrs/0022-tool-integration.md)).
- Each ingredient and each station declares the schema of its format, and an ingredient that fills a contract also
  declares the contract and its version. A release refuses one whose schema or contract version is newer than it
  supports, exit 1, naming the release needed ([ADR 0005](../adrs/0005-schema-compatibility.md)).

### What each part writes

An ingredient holds up to four parts: operations, renders, floor and checks ([ingredients](../features/ingredients.md)).
A station holds the ingredients and the recipes it pins, its stages, entries, templates and an optional detect rule
([stations](../features/stations.md)). Renders and floor stay owned by `the-cook` and are written on every render; a
station's pins, stages, entries and templates are written once, when it is applied, and the recipes it adds are copied
then, and all of them become the repository's own; operations, checks and detect rules write nothing.

- Entries are additions to `.cook/config.yaml` (commands, lanes, validators, module switches, workers, standing
  context), `.cook/policy.yaml` and `.cook/hooks.yaml` (post-edit hooks, lifecycle actions). A station's stages are
  entries of the profile: `vcs` and the `workers` they declare. Stack stations hold entries for `.cook/config.yaml`
  (commands, lanes, validators, module switches) and `.cook/hooks.yaml` (post-edit hooks) only, because permissions,
  actions and standing context are a repository's own choices. A station for a way of working that connects programs,
  such as `agentic-coding`, also holds the `unattended.deny` entries and lifecycle actions those programs need, such as
  a tracker's actions on `start`, `completed` and `blocked`, because running the lifecycle that way is exactly those
  entries. Why: no action runs that the repository does not declare in its own `hooks.yaml`, and a station's actions
  reach that file only when a person applies the station and reads its diff.
- A template is a whole file in a station or in `scaffold`, such as the configuration file of a loop runner a station
  uses. It is rendered once with values from the profile and written only when the file is absent; from then on it is
  the repository's own file, never rendered again and never removed by `the-cook`. When the file exists, applying
  reports `kept <path>: exists` and writes nothing to it. Why: a file another program reads is edited for that program
  by the team, and rewriting it would undo their edits. A recipe's `template` step is a different thing: it writes a
  file into a recipe run's workspace as part of the change the run makes ([below](#recipes)).
- A station pins the recipes it adds under the `recipes:` key of `station.yaml`, as it pins its ingredients: each a
  map of `name` and `sha256`, from the same cookbook tag as the station, such as
  `recipes: [{ name: implement-task, sha256: <64 hex> }]`. Applying the station verifies each recipe against that
  sha256 and copies it into `.cook/recipes/<name>/` with its `origin` ([below](#applying-a-station)). Why: a way of
  working usually brings the kinds of change it is for, such as the `implement-task` recipe the `agentic-coding`
  station adds; one tag keeps the station and its recipes consistent, and the pin means applying the station again
  never trusts a fresh download of the tag's `checksums.txt` for its recipes.
- Renders are files, keys and blocks `the-cook` writes on every `the-cook render` from the repository's files and an
  ingredient's render templates and floor, such as a worker ingredient's permission keys and hook entries in its
  worker's settings file, a whole rules file, or a loop runner ingredient's block in the ignore file. Renders come only
  from ingredients. The manifest records each one under the ingredient's name, the drift check covers it, and
  `the-cook remove` removes it
  ([generated-content-standards.md](generated-content-standards.md#kinds-of-generated-content)). Why: a program's native
  files follow the repository's neutral sources, so they are computed every time rather than edited
  ([ADR 0007](../adrs/0007-ownership.md), [ADR 0008](../adrs/0008-native-configuration.md)).

### The floor

An ingredient may mark deny rules and bypass arguments as floor. The floor is part of the ingredient's renders:
`the-cook` renders it into every worker's output on every render, never writes it into `.cook/policy.yaml`, and a
policy entry that would remove or weaken a floor rule is dropped from the output and reported
([above](#policy-entries)). A floor bypass argument is refused by `the-cook attempt` and by `attempt` actions. All
program floor comes from the ingredients the repository pins, and a station has none; typical floor rules are:

| Ingredient for | Floor |
| --- | --- |
| a version control system | deny a force push to the default branch the ingredient reports |
| a task runner | deny the syntax that chains a second task into one invocation |
| a worker | the arguments that bypass its permission checks, refused at every launch ([ingredients](../features/ingredients.md#the-floor)) |
| a jail | the arguments that turn its confinement off, refused wherever they appear in a jailed launch, `jail.args` included ([the jail contract](../features/ingredients.md#the-jail-contract)) |

- The `policy` block of `AGENTS.md` lists each floor rule with the ingredient it comes from, such as
  `(*floor rule of the git ingredient*)` for an ingredient named `git`. Why: the rule is enforced but is not in
  `policy.yaml`, so a reader of the permissions needs its source named
  ([generated-content-standards.md](generated-content-standards.md#block-ids)).
- The process that applies the floor is built into the binary: refusing before a launch, rendering a deny into every
  output, restoring and comparing the rendered files around an attempt. The program facts are the ingredient's. Why: a
  bypass argument or a chaining syntax changes when its program changes, while the guarantee that no repository data
  can lower it must hold for every program ([ADR 0011](../adrs/0011-safety-invariants.md),
  [ADR 0022](../adrs/0022-tool-integration.md)).
- A repository that does not pin an ingredient does not get its floor.
  `the-cook ingredient list --cookbook <source>@<version>` shows which ingredients of a tag detect their program in the
  repository, so a person finds a program in use whose ingredient, and floor, the repository does not pin. Why: a
  program in use without its ingredient runs without the ingredient's floor, and seeing the gap turns it into a
  decision a person makes.

### Applying a station

- A station is applied only on request: by `the-cook scaffold --cookbook`, to the files it seeds (the stations of that
  tag whose detect rule matches, or only those `--station` names with `--no-detect`), and by
  `the-cook station apply <name>...` at any later time, with `--cookbook <source>@<version>` for a station from a
  cookbook tag, or without it for a station `stations` already records, at its pinned version. No other command writes a
  station's pins, entries or templates. Why: every write to a repository's files is then one a person asked for.
- Applying records the station in `stations`, pins in `ingredients` each ingredient the station lists, at the sha256 the
  station lists, writes the station's stages, entries and templates, and copies each recipe its `recipes:` key pins,
  verified against the sha256 the station lists, into `.cook/recipes/<name>/` with its `origin`, exactly as
  `the-cook recipe add` does, in the same diff. It checks the `requires` of each recipe it copies against the profile as
  that diff leaves it, so the workers and lanes the station adds count, and a missing contract, role or lane exits 3,
  names it and writes nothing. A recipe directory that already exists is kept and reported
  (`kept .cook/recipes/implement-task: exists`), never overwritten, because the recipe there is the repository's own;
  `the-cook recipe update` merges a newer version into it. An ingredient the repository already pins at another sha256
  keeps the repository's pin, and the difference is reported
  (`kept ingredients.git: pinned at v1.1.0, station agentic-coding lists v1.2.0`). Why: moving a program's pin is its
  own reviewed decision, made with `the-cook ingredient pin`.
- Applying adds only the entries the repository lacks, and never removes or reorders anything. Why: the repository's own
  entries stay authoritative, and applying a station again adds only what is missing, an entry the repository deleted
  included, so its diff shows exactly what a person is about to take.
  - A list entry (a hook declaration, a validator) is added when no equal entry exists, because an equal entry already
    says the same thing.
  - A named item (a command, a lane, a worker) is added only when the repository has none by that name. When the
    repository's item differs from the station's, the repository's stays and the difference is reported
    (`kept lanes.unit: differs from station node`). Why: an item the repository names is its decision, made and
    reviewed there; replacing it would undo that decision, and reporting the difference lets a person choose.
  - A scalar key (such as `modules.verify` or `vcs`) is written only when absent, because a value that is present is the
    repository's choice, even when it equals the default. A present value that differs from the station's is reported
    like a kept item, so nothing the station would have changed goes unmentioned.
- Comments, key order and blank lines are preserved. Why: people annotate these files, and the diff of an application
  must show only its additions.
- The additions, pins, entries, templates and recipes alike, are shown as one diff and confirmed before anything is
  written. `--yes` answers the question. `--dry-run` prints the diff and writes nothing. Without a terminal and without
  `--yes`, nothing is written and the command exits 3
  ([cli-standards.md](cli-standards.md#confirmations---yes-and---force)). Why: once written, the entries are the
  repository's own and nothing marks where they came from, so the diff before writing is the one moment a person sees
  them as a station's additions.
- After writing, the command renders the repository as `the-cook render` does, with every ingredient the station lists
  downloaded when it is not cached and verified against the sha256 the station lists, so the additions and their
  rendered effect reach review as one diff and the drift check passes on it. A failed download or verification writes
  nothing.
- `stations` records which stations were applied and at which pin, and the manifest records the ingredient that owns
  each render. Nothing records which entries a station added, neither the manifest nor a comment nor a header in a
  template. Why: each entry then has one source of truth, the file it is in, and the tool has nothing it could later use
  to change those entries by itself. A report built on such a record would also nag a repository that removed an entry
  on purpose. A recipe the station copied carries its own `origin`, as every added recipe does, and it names the
  cookbook tag the recipe came from, not the station.
- Deleting a name from `stations` changes nothing rendered. Deleting a name from `ingredients`, once neither `vcs` nor a
  worker names it, and rendering removes that ingredient's renders, floor included. A station's applied entries and
  templates stay as the repository's own: they are changed or removed by editing the file, like any other entry, and
  removing `the-cook` (`the-cook remove`, `the-cook remove --eject`) deletes the seeded files in `.cook/` whatever they
  hold, deletes `.cook/recipes/` after listing each recipe, and leaves every file a template wrote, so applying a
  station's entries has no inverse ([cli-standards.md](cli-standards.md#command-grammar)).
- A newer version of a station may hold more entries, list newer ingredients or add more recipes. They reach a
  repository only when someone applies that version:
  `./cook station apply node --cookbook github:acme/cookbook@v1.3.0 --dry-run` shows what it would add, and applying it
  moves the station's entry in `stations` to that version. Ingredient pins the repository holds stay where they are;
  `the-cook ingredient pin` moves one, and the newer ingredient's renders and floor take effect in the render that
  moving the pin performs, under the rules for
  [ingredients and stations across versions](compatibility-standards.md#ingredients-and-stations-across-versions). A
  recipe the repository already has is kept; `the-cook recipe update` merges a newer version into it
  ([recipes across versions](compatibility-standards.md#recipes-across-versions)). Why: moving a pin changes the
  description a repository uses, never the entries or the recipes it owns.

## Recipes

A recipe is one kind of change the repository makes many times, such as a database migration or a new module: its
inputs, what it requires, its steps mapped onto the lifecycle's stages and the lanes it requires. It lives in
`.cook/recipes/<name>/recipe.yaml`, with the sources of its `template` steps in `files/` beside it, and it is the
repository's own: a recipe the repository wrote, or a copy added from a cookbook that records its `origin`.
`the-cook make <name>` runs it, one change per recipe run ([recipes](../features/recipes.md)). Why: a generator that
writes files stops before verification, and a skill alone guarantees nothing, while a recipe run carries both the
deterministic part and the judgement of a repeated change through the same guards as any other work
([ADR 0025](../adrs/0025-recipes.md)).

```yaml
# .cook/recipes/migration/recipe.yaml
schema: 1
name: migration
description: Add a database migration
inputs:
  name: { type: slug, description: what the migration does }
requires:
  contracts: [vcs, worker]
  roles: [unattended]
  lanes: [unit, migrations]
workspace: "migration-{name}"
steps:
  - run: [npm, run, migration:create, --, "{name}"]
  - template: { from: down-test.ts, to: "src/migration/{name}.spec.ts" }
  - attempt: { worker: coder, prompt: "Write the up and down steps of the migration {name}." }
  - handoff: "Check the generated SQL against production data volumes, then continue."
verify: { require: [unit, migrations] }
```

A recipe added from a cookbook carries one more key, which `the-cook recipe add` writes:

```yaml
# .cook/recipes/nestjs-module/recipe.yaml, beside the keys above
origin:
  source: github:acme/cookbook
  version: v1.2.0
  sha256: 9a7e3ac8721dc0655def03e198e417b1fa1a4b3c21eac59da309d06c0e6ff7d4
```

| Key | Type | Meaning |
| --- | --- | --- |
| `schema` | integer | the recipe format's schema; a release refuses a newer one, exit 1, naming the release needed |
| `name` | recipe name | the recipe's name, `[a-z][a-z0-9-]*`, equal to its directory's name ([cli-standards.md](cli-standards.md#command-grammar)) |
| `description` | string | one line, printed by `the-cook recipe list`, by `the-cook make <name> --help` and when a run starts |
| `origin` | map: `source`, `version`, `sha256` | the cookbook, tag and archive sha256 a copied recipe came from: written by `the-cook recipe add` and by applying a station that adds the recipe, moved by `the-cook recipe update`, conflicts or not; absent for a recipe the repository wrote |
| `inputs.<input>` | map: `type`, `description`, and `values` for an `enum` | one input, named `[a-z][a-z0-9-]*` and given to `the-cook make` as `--<input> <value>`; `type` is `string`, `slug` (`[a-z0-9][a-z0-9-]*`), `enum` (one of `values`) or `bool` |
| `requires.contracts` | list of `vcs`, `worker` | the contracts a pinned ingredient must fill |
| `requires.roles` | list of `interactive`, `unattended` | the roles a declared worker must have |
| `requires.lanes` | list of lane names | the lanes the profile must declare |
| `workspace` | string | Start: the name of the managed workspace a run creates, on branch `work/<name>` at the revision of the shared default line of work, as `the-cook workspace create` makes them |
| `steps` | list of steps | Implement: the steps, in order, each a map with exactly one kind key, below |
| `verify.require` | list of lane names | Verify: lanes the run's Verify stage runs, never skipped, on top of the lanes the change selects; one the change selects nothing for runs in full; each is also in `requires.lanes` |

| Step kind | Value | What it does |
| --- | --- | --- |
| `run` | list of strings | runs the argument list in the workspace as a child process of `the-cook`, like a `run` action, never through a shell; a non-zero exit stops the run |
| `template` | map: `from`, `to` | writes the path `to` in the workspace from the file `from` in the recipe's `files/`, rendered in the [template language](../features/ingredients.md#the-template-language) with the inputs and `task` as its values; a path that exists stops the run |
| `attempt` | map: `worker`, `prompt`, `model`, `effort` | starts `worker`, a declared worker with role `unattended` (by default the only one), on `prompt` (by default the prompt the caller passes on stdin), in the workspace, under the attempt contract; its edits stay for the next steps, a `the-cook verify` its worker runs is feedback for the worker and not the run's proof, and a failed attempt stops the run |
| `handoff` | string | prints the text for whoever drives the run and exits 3: in an attended run it pauses the run and fires nothing, and `the-cook make --continue` resumes after it; in a run the caller marks `--unattended` it stops the run as blocked |

- The step kinds are a closed set the binary defines; a step with any other key, or with two kind keys, is a validation
  error. Why: each kind is a process the binary runs under its guards, and a kind a file could define would run
  something the binary cannot bound.
- `{<input>}`, where `<input>` is an input the recipe declares, and `{task}`, the task id given with `--task`, are the
  only substitutions in the recipe file, and they apply in `workspace`, in each element of `run`, in `template.to`, in
  `attempt.prompt` and in `handoff`. A placeholder that names no declared input is a validation error, and `{task}` in a
  run without `--task` exits 1 before anything runs. Why: no element passes through a shell, so a substituted value
  stays one argument, one path or plain text, and a misspelt placeholder fails before anything runs instead of reaching
  a program as written. The sources in `files/` are rendered in the template language instead, because they are whole
  files whose own text may hold braces.
- Inputs are validated by their type before anything runs, the workspace included. An input name is lower case ASCII
  words joined by hyphens, `[a-z][a-z0-9-]*`, and never the name of a flag of `the-cook make` (every flag it accepts,
  global flags included: `continue`, `status`, `task`, `here`, `unattended`, `commit`, `withhold`, `dry-run`, `yes`,
  `help`, `json`, `verbose`), because the input is itself a flag of `the-cook make`
  ([cli-standards.md](cli-standards.md#naming)). A `run` step's environment carries it as `COOK_INPUT_` and the name in
  upper case with hyphens as underscores: `module-path` gives `COOK_INPUT_MODULE_PATH`. A `bool` input is a boolean
  flag, true when present and false when absent; every other input must be given, and none has a default
  ([cli-standards.md](cli-standards.md#booleans)). `the-cook make <recipe> --help` lists the recipe's inputs with their
  types and descriptions.
- No input value of any type may begin with a hyphen; validation refuses one before anything runs. An input
  substituted into a `run` element is then one argument whatever it holds, and never an option of the program it
  reaches.
- `template.to` is a path relative to the workspace, with forward slashes. One that, after substitution, is absolute or
  leaves the workspace is refused before anything runs, exit 1, because a recipe run writes only its own workspace.
- In `attempt.prompt` and `handoff`, inputs are plain-text substitution and a prompt-injection surface, as in an
  `attempt` action's prompt ([cli-standards.md](cli-standards.md#values-passed-to-user-declared-commands-and-actions)).
- `requires` is checked when the recipe is added and again when it runs; a missing contract, role or lane exits 3 and
  names it ([cli-standards.md](cli-standards.md#the-make-command)).
- A recipe can only add: steps, required lanes and requirements. It declares no lifecycle action, skips no lane,
  weakens no guard and fires no event itself; delivery of a recipe run's work follows the repository's own lifecycle
  actions. Why: a recipe is repository data that a worker may have written, and the guards and the delivery convention
  hold whatever describes the work ([ADR 0015](../adrs/0015-development-lifecycle.md)).
- A run reads its recipe once, when it starts, from the base commit of the workspace it creates, the revision of the
  shared default line of work that the version-control ingredient's `default_revision` reports, and keeps the recipe's
  content and digest in its run record, so a recipe runs in a new workspace once it is on that line (for git, pushed),
  not merely committed in one clone; `the-cook make --continue` runs that content and never reads the file again, so no
  step changes the recipe of the run it is in. `the-cook make --here` reads the file in the working tree instead, for
  drafting a recipe, and is refused with `--unattended`, so a run nobody drives never follows a draft nobody reviewed
  ([cli-standards.md](cli-standards.md#the-make-command)). The tamper check of an `attempt` step covers `.cook/recipes/`
  with the rest of `.cook/`, so its worker cannot change a recipe a later run reads either.
- The run's Verify stage runs the lanes itself: every lane the change selects, as the receipt a step left in the
  workspace selects them, and every lane in `verify.require`, a required lane the change selects nothing for in full.
  The receipt the run writes records per lane the patterns it ran or that it ran in full
  ([verify](../features/verify.md)), and Complete replays it from the base, as for any work.
- `origin` is a record, not a source: `the-cook make` never reads it, and `the-cook recipe update` and `the-cook fetch`
  read it to find the version the copy came from ([Inputs](#inputs)). A recipe without `origin` has no version to merge
  from and changes only by editing.

## No secrets

Why: the repository files are committed and readable by everyone with access to them, and a secret in a committed
file is published.

- The repository files in `.cook/` MUST NOT contain tokens, passwords, keys or internal credentials, in values,
  comments, action commands, URLs or headers.
- A setting or action that needs a credential refers to an environment variable by name (`url_env`, `headers_env`, or a
  variable a `run` command reads), or relies on the authentication of the tool it calls (the version control system, the
  hosting platform's CLI). Actions inherit the environment of the process that fires them; that environment, never a
  file, carries the credential. Every action of a repository can read every variable in that environment, so a webhook
  secret is visible to all of them.
- `the-cook` never prints the value of an environment variable it reads. A lifecycle dry run prints only the `COOK_*`
  variables `the-cook` sets for the action. The lifecycle log never records URLs, headers, bodies or prompts.
- Personal preferences belong in the workers' own local override files or in `COOK_*` environment variables. `the-cook`
  never writes those files and reads them only through `the-cook scaffold --import local`. What that import copies into
  `policy.yaml` becomes committed and shared, and is marked for review in the to-do.

## What never becomes a setting

The following are not keys in any file or flag. They are architecture decisions: changing one is a change to the ADR
that decides it, because a setting that weakens them would copy along with the file that sets it
([ADR 0011](../adrs/0011-safety-invariants.md),
[ADR 0013](../adrs/0013-organization-content.md), [ADR 0014](../adrs/0014-directory-layout.md),
[ADR 0017](../adrs/0017-engine-version.md), [ADR 0021](../adrs/0021-release-trust.md),
[ADR 0022](../adrs/0022-tool-integration.md), [ADR 0025](../adrs/0025-recipes.md),
[ADR 0026](../adrs/0026-cookbook-trust.md)).

- An attempt never bypasses permissions: every argument a worker's ingredient marks as a floor bypass argument is
  refused.
- The floor of the ingredients in use, such as a version-control ingredient's deny of a force push to the default
  branch and a task runner ingredient's deny of task chaining, is rendered on every render and no policy entry removes
  or weakens it ([ADR 0022](../adrs/0022-tool-integration.md)).
- Verification proofs are replayed outside the worker's writable tree, and completion is withheld until the replay
  passes.
- Hooks never decide permissions.
- The ingredients an attempt uses are resolved from the per-user cache before its worker starts, and nothing is fetched
  during a hook or an attempt.
- Tool-owned files are restored from the base commit before `the-cook attempt` or an `attempt` action starts its worker
  and compared after it, together with the files under `.cook/` and the files standing context names; any difference
  fails the attempt, and so does a base that cannot be read. A failed `the-cook attempt` attempt returns the checkout to
  its base, keeping its changes in `last-attempt.patch`, so the next attempt starts from checked files.
- Lifecycle actions never run inside an attempt or an `attempt` action: the binary refuses `lifecycle run` there, and
  it renders `[the-cook, lifecycle]` into the unattended denies of every worker for every rendered spelling.
- An `attempt` action runs under the attempt contract and cannot commit, push or fire lifecycle events.
- `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
  without a valid receipt for the work's base.
- Standing context for an attempt is read from the base commit.
- No action runs unless the repository declares it; the binary contains code and data for no program, the version
  control system and the workers included, and no logic for the platform that hosts a team's work; it reads no
  other program's configuration, and is never an MCP client.
- The binary never writes knowledge and never writes the team's record of work (issues, projects, boards, comments,
  reviews, labels).
- The binary never reconfigures state it did not create.
- What `the-cook` renders and runs comes only from the repository's files, its pinned release and the ingredients it
  pins by hash; no repository file names a source outside the repository for it except a release, an ingredient or a
  station pinned by hash. A recipe's `origin` records where its copy came from and is never read to run it.
- The binary ships no ingredient, no station and no recipe and names no default cookbook; every ingredient a repository
  uses is pinned in `ingredients`, and every station it applied is recorded in `stations`, each by cookbook, version and
  sha256, and two entries of one list cannot share a name.
- A station's entries are applied only on request and only add; a template is written only where its file is absent; a
  station has no renders and no floor; an ingredient's renders are recorded in the manifest under its name; nothing
  records which entries a station added.
- The step kinds of a recipe are a closed set the binary defines. A recipe only adds steps, required lanes and
  requirements: it never skips a lane, weakens a guard, fires an event or declares a lifecycle action. A recipe run
  reads its recipe once, from the base commit of its workspace unless a person drafting it runs it from the working
  tree, and never again; its Verify stage runs every lane the change selects and every lane the recipe requires; and a
  handoff in a run the caller marks unattended stops it as blocked.
- The managed-block marker syntax, the block classes, the generated-file header and the manifest location.
- The locations of the configuration of `the-cook` (`.cook/`) and of the content workers read (`.agents/`); only
  state paths are settings.
- The version of `the-cook` is pinned only in `.cook/wrapper.yaml`.
- Implicit entry points (rendered worker hooks) never download a binary.
- The wrapper never caches or executes a downloaded binary whose checksum does not match `wrapper.yaml`, and downloads
  only over HTTPS.
- A pin's checksums are written only after the running binary verifies the release's signed checksum file; a pending
  pin admits no download.
- Whether the workers' local override files are written: they never are.
