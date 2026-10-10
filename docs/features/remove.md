# remove

## Objective

**Problem.** A team that tried an agent setup in a repository, or no longer wants one of its practices, has to take out
what was added across many shared files without touching what people wrote themselves. A piece left behind keeps acting
on every agent session, and a piece removed too eagerly takes a teammate's settings or hand edits with it. A team that
stops using the tool may still want to keep the working agreements, permissions and hooks it relies on, as its own
files.

**Goal.** Taking out the whole setup or one practice is one step that removes exactly what was added, and either
restores the setup the repository had before or leaves the result in place as the team's own files. No hand edit to
generated content, and nothing people wrote themselves, disappears without anyone noticing.

## Overview

`the-cook remove` takes `the-cook`, or one of its modules, out of a repository, in one of two modes. **Undo**, the
default, deletes what `the-cook` added and returns the repository to the setup it had before adoption. **Eject**,
`--eject`, stops `the-cook` managing the repository and keeps the result: its convention blocks (the marked sections of
`AGENTS.md` and `.gitignore` that state a convention, such as where worktrees live), permissions and hooks stay as
ordinary hand-owned files. Both work from the [manifest](concepts.md#ownership-and-the-manifest), and both refuse to
delete a file someone edited by hand.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## When to use it

| Situation | Command |
| --- | --- |
| The adoption was a trial; the repository should go back to how it was before `the-cook` | `./cook remove` |
| The repository should stop using `the-cook` and keep its worktree and memory conventions, its permissions and its hooks | `./cook remove --eject` |
| The repository no longer wants one module, for example [memory](memory.md), its agent memory under `.agents/memory/` | `./cook remove --module memory` |
| The repository no longer uses one program, such as Codex | delete its recipe from `recipes:` in the profile and run `./cook render` ([Removing one recipe](#removing-one-recipe)) |
| You want to see what a mode would do | add `--dry-run` |

Turning a module off in the profile and running `./cook render` has the same effect as `remove --module`, except
that `remove --module` also sets the module to `false` in the profile for you.

## Undo and eject compared

The [manifest](concepts.md#ownership-and-the-manifest) separates two kinds of content, and the two modes treat them
differently ([ADR 0007](../adrs/0007-ownership.md)). **Owned** content is what `render` writes and rewrites. **Seeded**
files are the ones `the-cook` wrote once and the repository has owned since: `.cook/config.yaml`,
`.cook/policy.yaml` and `.cook/hooks.yaml`, from `scaffold`, and the `.agents/memory/` skeleton, from `scaffold`
or the first `render` after the memory module was turned on. The manifest records each with the hash it was created
with. Managed blocks are owned, and each is either a convention block or a tool block
([block ids](render.md#block-ids)).

Every owned output belongs to the binary or to one [recipe](recipes.md#renders), and the manifest records which. The
rows below apply to every recipe's renders alike, such as the claude recipe's keys in `.claude/settings.json`, the
codex recipe's rules file and the ralph-tui recipe's `.gitignore` block.

| What | `remove` (undo) | `remove --eject` |
| --- | --- | --- |
| Seeded files in `.cook/`: profile, policy, hooks file | deleted, listed first | deleted |
| Owned files in `.cook/`: `wrapper.yaml`, the manifest | deleted | deleted |
| Other files in `.cook/`, such as a script the team keeps there | listed and left; `.cook/` is deleted once empty | listed and left; `.cook/` is deleted once empty |
| `cook` | deleted | deleted |
| Convention blocks: `worktrees`, `policy`, `memory`, `context` in `AGENTS.md`; `import` in `CLAUDE.md`; every `.gitignore` block | removed, text and entries included | markers removed, text and entries kept |
| Tool blocks: `commands`, `layout`, `verify`, `lifecycle` in `AGENTS.md`; `commands` in `README.md` | removed | removed |
| Permissions in `.claude/settings.json`, and `.codex/rules/the-cook.rules` | removed; the rules adoption imported from the project's native files are written back as plain settings | kept as hand-owned content; the generated header is removed; entries that name a `the-cook` command (every rendered spelling) are removed and listed |
| `raw` hooks | removed; the hooks adoption imported from the project's native files are written back as plain hooks | kept as plain hooks |
| Hook entries that run `cook`: modelled hooks, notification hooks, context delivery, the Codex `notify` key | removed | removed, and listed |
| Other owned keys, such as the `.codex/config.toml` keys that enable project hooks | removed | kept as hand-owned content |
| The shipped skill and its links | deleted | deleted |
| The `.agents/memory/` skeleton | a file whose content still matches the hash it was seeded with is deleted; an edited file is knowledge and stays, listed | kept |
| Knowledge: `AGENTS.md` prose, edited memory, domain skills, `.agents/commands/`, `.agents/REVIEW.md` | kept | kept |
| Templates a [recipe](recipes.md#templates) wrote, such as `.ralph-tui/config.toml`: the repository's own files | kept | kept |
| State: the [state paths](profile.md#state-paths) (default under `.work/`) | left in place and listed; no longer ignored | left in place, still ignored |

Neither mode touches `settings.local.json`, user-level settings, other tools' files such as `.beads/`, or anything
`the-cook` did not create. The manifest records no template a recipe wrote, so removal never deletes one.

## How it works

Both modes show what they will delete and rewrite, and ask before doing it. `--yes` skips the question, and without a
terminal and without `--yes` they stop with exit 3. Both start the same way:

1. Read the manifest.
2. Check each owned path's hash. A path whose content changed since the last render is refused, and the command stops
   before deleting anything. `--force` goes ahead anyway. Seeded files belong to the repository, so an edit to one is
   expected and never refused.

### Undo

3. List the seeded files that will be deleted: the profile, the policy, the hooks file, and the memory skeleton files
   nobody edited. Without `the-cook` they have no reader.
4. Delete owned files and links: `cook`, `.cook/wrapper.yaml`, the shipped skill and its links, and every file a
   recipe renders, such as `.codex/rules/the-cook.rules`.
5. Remove every managed block, convention and tool blocks alike, from `AGENTS.md`, `README.md`, `CLAUDE.md` and
   `.gitignore`, markers and text. A file left empty once its blocks are gone is deleted.
6. Remove owned keys and entries from native files. Every key no render owns keeps its bytes.
7. Write back what adoption imported from the project's native files, as plain native settings: the permission rules
   and hooks whose provenance comment in `.cook/policy.yaml` or `.cook/hooks.yaml` names a project-scope file,
   such as `# imported from .claude/settings.json (project)`. Entries imported from the `local` or `user` scope are
   not written back: their source files were never changed and still hold them.
8. Delete the seeded files and the manifest, then `.cook/` once nothing else is in it.
9. List the state paths left in place. Their `.gitignore` blocks are gone, so they show up as untracked files until
   you delete them or ignore them yourself.

Entries the repository added after adoption leave with `.cook/policy.yaml`; they stay in git history.

### Eject

3. Remove the markers of every convention block; its text, and every `.gitignore` entry, stays where it is.
4. Remove every tool block, markers and text. These blocks describe `the-cook` commands, which do not exist after an
   eject.
5. Keep the rendered native files and owned keys as hand-owned content, and remove their generated headers. Remove every
   hook entry that runs `cook`, the Codex `notify` key when it does, and every permission entry that names a
   `the-cook` command in any rendered spelling, and list them. `raw` hooks stay.
6. Delete the shipped skill and its links, `cook`, and the owned and seeded files in `.cook/`, the manifest
   included; then `.cook/` once nothing else is in it.

After an eject the repository keeps working without `the-cook`:

| Kept and working | Gone |
| --- | --- |
| Agents' permissions, from the native files | `cook` and every `the-cook` command |
| `raw` hooks | modelled hooks (post-edit commands, validators, session context), notification hooks and hook-delivered context, which ran through `the-cook hook` |
| Convention blocks in `AGENTS.md`: where worktrees live, the permissions agents work under, the memory index, `static` context | the tool blocks: the commands, the layout, the verification checks and the delivery steps |
| `.gitignore` entries for every state path | lifecycle events and their actions; the receipt, its replay and the drift check |
| `bd`, the Beads task tracker's CLI, with its `.beads/`; the templates a recipe wrote, such as `.ralph-tui/config.toml` | `the-cook worker`, its worker contract and tamper check; an agent command that names `./cook worker`, such as one in `.ralph-tui/config.toml`, fails until the team changes it |

### Removing one module

`--module <name>` is an undo of one module: it removes only what that module produces and sets
`modules.<name>: false` in the profile.

| Module | Removed |
| --- | --- |
| `hooks` | `the-cook hook` entries in native files, including notification hooks and the delivery of [standing context](context.md) by hook (`raw` hooks stay as plain hooks; `static` context stays in `AGENTS.md`) |
| `verify` | the module's allow entries, the `verify` block |
| `memory` | the `memory` block, and the skeleton files that still match the hash they were seeded with; edited files stay, listed: they are knowledge |

Repeat `--module` to remove several. `--module` changes only `modules.<name>` in the profile: `vcs:`, `recipes:` and
every other key are repository data and stay.

### Removing one recipe

No flag of `remove` takes out one recipe. Delete its name from `recipes:` in the profile and run `./cook render`:
the manifest records each render under the recipe that owns it, so `render` removes exactly that recipe's files, keys
and blocks, and its [floor rules](recipes.md#the-floor) leave every agent's output. What the recipe applied once, its
entries in the repository files and its templates, stays as the repository's own; edit or delete them like anything
else. `vcs:` and `agents.<name>.recipe` must not name a recipe `recipes:` no longer lists, or `render` exits 1.

```sh
$EDITOR .cook/config.yaml        # delete "- codex" from recipes: and the codex agent from agents:
./cook render
git status --short                  # .codex/rules/the-cook.rules deleted, .codex/hooks.json entries removed
```

## Reference

```text
the-cook remove [--module <name>]... [--force] [--dry-run]
the-cook remove --eject [--force] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--eject` | off | Stop managing the repository and keep the result. Cannot be combined with `--module` |
| `--module <name>` | all | Remove one module: `hooks`, `verify`, `memory`. Repeatable |
| `--force` | off | Go ahead even when owned paths changed since the last render |
| `--dry-run` | off | Print what would be deleted, rewritten, restored or kept; change nothing |

Global flags `-C <dir>`, `--yes` and `--verbose` also apply; see [CLI reference](cli.md).

| Exit code | Meaning |
| --- | --- |
| 0 | Removed, or ejected |
| 1 | Error, for example no manifest, or `--eject` with `--module` |
| 3 | An owned path was modified by hand, so nothing was changed: inspect it, then rerun with `--force` or restore it. Or confirmation was needed without a terminal: rerun with `--yes` |

Files read: the manifest and every path it lists; `.cook/policy.yaml` and `.cook/hooks.yaml`, for the
provenance of imported entries. Files written: those paths; `.cook/config.yaml` with `--module`.

## Examples

### An undo

```sh
$ ./cook remove --dry-run
seeded files that would be deleted: .cook/config.yaml .cook/policy.yaml .cook/hooks.yaml
                                    .agents/memory/README.md (unedited)
would delete: cook .cook/wrapper.yaml .cook/manifest.json .agents/skills/the-cook/SKILL.md
              .claude/skills/the-cook .codex/skills/the-cook .codex/rules/the-cook.rules
would remove blocks: AGENTS.md (commands, layout, worktrees, verify, lifecycle, policy, memory),
                     README.md (commands), CLAUDE.md (import), .gitignore (state, worktrees)
would remove: .claude/settings.json (permissions, hook entries), .codex/hooks.json (hook entries)
would restore: .claude/settings.json (12 permission rules, 1 hook imported from project files)
left in place, no longer ignored: .work/
$ ./cook remove --yes
$ rm -rf .work
$ git status --short
```

### An eject

```sh
$ ./cook remove --eject --dry-run
would delete: cook .cook/ .agents/skills/the-cook/SKILL.md .claude/skills/the-cook .codex/skills/the-cook
would remove hook entries that run cook: .claude/settings.json (SessionStart, PostToolUse, Notification, Stop),
                                             .codex/hooks.json (SessionStart, PostToolUse), .codex/config.toml (notify)
would remove tool blocks: AGENTS.md (commands, layout, verify, lifecycle), README.md (commands)
would keep as plain text: AGENTS.md (worktrees, policy, memory), CLAUDE.md (import),
                          .gitignore (state, worktrees)
would keep as hand-owned: .claude/settings.json (permissions), .codex/rules/the-cook.rules, .codex/config.toml (hook keys)
$ ./cook remove --eject --yes
$ git diff AGENTS.md
```

The worktrees convention in `AGENTS.md` loses its markers and keeps its text:

```diff
-<!-- cook:worktrees -->
 Worktrees in this project are created in `.work/worktrees/<name>`, on branch `work/<name>`, from committed code.
-<!-- /cook:worktrees -->
```

### One module

Drop memory from an Acme Go repository whose agents keep no memory files:

```sh
./cook remove --module memory
git commit -am "chore: drop the-cook memory module"
```

## Guidelines

- Do commit or stash your work first, so `git diff` shows only what `remove` did.
- Do read the `--dry-run` output for a repository you did not set up.
- Do eject, rather than undo, when people or agents rely on the conventions; undo when the adoption was a trial.
- Do change every agent command that names `./cook worker`, such as those in `.ralph-tui/config.toml`, when the
  repository stops using `the-cook`: removal keeps the templates a recipe wrote as they are.
- Do write the checks and the delivery steps into `AGENTS.md` prose after an eject, if agents should keep following
  them: the `verify` and `lifecycle` blocks are tool blocks and leave with the tool.
- Do delete, or ignore yourself, the state paths an undo lists.
- Don't use `--force` before looking at why a path changed: a hand edit there may be the only copy of a decision that
  belongs in a repository file.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Exit 3 naming a file | The file changed since the last render | Move the meaningful change into a repository file, or rerun with `--force` |
| `CLAUDE.md` was deleted by an undo | It held only the `import` block, so it was empty once the block was removed | Expected; recreate it if you want one. An eject keeps the `@AGENTS.md` line |
| `.work/` shows up in `git status` after an undo | Its `.gitignore` block was removed with the rest | Delete it, or add your own ignore entry |
| The agent lost its lint-on-edit hook | Modelled hooks run through `the-cook hook`; `--module hooks`, undo and eject all remove them | Re-enable `modules.hooks` and render, or write the command as a plain hook in the agent's settings |
| The lanes and delivery steps are gone from `AGENTS.md` after an eject | `verify` and `lifecycle` are tool blocks, and an eject removes tool blocks | Write the checks you still want into `AGENTS.md` prose |
| An undo kept a memory file | The file changed since it was seeded, so it is knowledge | Expected; delete it yourself if the repository no longer wants it |
| `.cook/` is still there after a removal | It holds a file `the-cook` neither owns nor seeded, such as a script the team keeps there; the output listed it | Delete the file, or keep it, as you choose |
| ralph-tui cannot start an agent after a removal | `.ralph-tui/config.toml`, a template the `ralph-tui` recipe wrote, names `./cook worker`, which removal deleted | Point each agent's `command` and `defaultFlags` at the agent, or at a wrapper of your own |
