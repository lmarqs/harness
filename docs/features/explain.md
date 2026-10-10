# explain

## Objective

**Problem.** A developer or a coding agent about to edit a repository's permissions, hooks or settings, or puzzled by a
refusal, needs the rules and the reasons behind them for the exact release the repository runs. Documentation on the web
may describe another release or be out of reach offline, and rules pasted into prompts and instruction files go stale
with the next release. A person and a coding agent who read different texts end up following different rules.

**Goal.** Anyone working in a repository, online or offline, can read the rules and their reasons as they hold for the
release that repository runs, and a person and a coding agent asking the same question get the same answer.

## Overview

`the-cook explain` serves no single stage of the [development lifecycle](lifecycle.md): it explains the rules of every
stage, at any point of the work.

`the-cook explain <topic>` prints the built-in documentation for one topic: what the standard is, and the reasoning
behind it. It is the same text the [shipped skill](skills.md#the-shipped-skill) carries, so a person at a terminal and
a worker in a session learn the same rules.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## When to use it

- A worker must edit `.cook/policy.yaml`, `.cook/hooks.yaml` or the profile correctly: `explain policy`,
  `explain hooks`, `explain profile`.
- A coordinating worker is finishing an adoption, that is, resolving the to-do `the-cook scaffold --adopt` leaves after
  it takes over a hand-written setup ([scaffold](scaffold.md#adopt)): `explain adoption`.
- Someone asks why `the-cook` refuses something: `explain invariants`, `explain safety`.
- Someone asks what happens at each stage of a change, or a worker declares or triggers lifecycle actions or
  notifications: `explain lifecycle`, `explain hooks`.
- Someone wants a stack's starting commands, [lanes](verify.md) and hooks, or reads a line such as
  `kept lanes.unit: differs from station node` from `station apply`: `explain stations`.
- Someone asks how `the-cook` works with the version control system, a worker or another program, or where a
  repository's ingredients come from: `explain ingredients`.
- Someone wants a directive or a skill applied without invoking it each time: `explain context`.
- Someone connects a loop runner or a task tracker: `explain attempt`, `explain lifecycle`, `explain stations`.
- Someone asks how the repository pins, downloads and verifies the release it runs: `explain wrapper`.
- Something behaves unexpectedly: `explain pitfalls`.
- You are offline and need the reference for the pinned release: the text is embedded in the binary.

## How it works

The text is embedded in the binary and matches its version. Through `./cook`, that binary is the release the
repository pins in `.cook/wrapper.yaml`. `explain` reads nothing from the repository and writes nothing. Output is
Markdown on stdout.

## Topics

| Topic | Explains |
| --- | --- |
| `layout` | the `.cook/` and `.agents/` directories and who owns each, `cook`, native directories, links, the state paths |
| `profile` | `.cook/config.yaml` fields, `ingredients:`, `stations:`, `vcs:` and `workers:` among them, and how to choose values |
| `policy` | `.cook/policy.yaml`, the three entry forms, rendering per worker and per spelling of a `the-cook` command, precedence, the floor rules of the ingredients in use, how to place an entry |
| `hooks` | `.cook/hooks.yaml`, the hook runtime, the `{file}` placeholder, notification hooks on the worker events `attention` and `turn_end`, the hook command and why it never downloads |
| `lifecycle` | the stages of a change, who acts at each and what each reaches through, lifecycle events including `blocked`, trigger points, scopes, task binding with `workspace create --task`, the action kinds `run`, `http` and `attempt`, the built-in guards, the `COOK_*` environment, `lifecycle.jsonl` in the state directory |
| `context` | standing context: `always` and `when` entries, triggers per worker, `content` and `pointer`, roles, `static` entries, trusted sources, the budget, `the-cook context show` |
| `adoption` | adoption classes, import scopes, the equivalence check, the to-do, how a coordinating worker finishes an adoption |
| `verify` | lanes, skip reasons, the receipt and its freshness rules |
| `attempt` | `the-cook attempt`: the attempt contract and its tamper check, rules read from the base commit, the replay, `--commit` and `--withhold`, its exit codes, how a loop runner names it as the command it runs for each task, who fires the events of a run |
| `invariants` | the invariants built into the binary, the floor rules they apply from ingredients, and why none can be turned off |
| `blocks` | managed blocks, their markers, the per-module and per-ingredient ids, convention blocks and tool blocks, the `AGENTS.md` budget, the editing rules |
| `ingredients` | ingredients and their parts (operations, renders, floor, checks), the version-control and worker contracts, floor rules, the pin of each declared ingredient (cookbook, version, sha256), how pinning is trusted and reviewed, fetching and the cache, what a command does when no ingredient fills a contract or an ingredient is not cached, `the-cook ingredient pin`, `list` and `show`, `the-cook fetch` |
| `stations` | stations and their parts (ingredients, stages, entries, templates, detect rule), cookbooks, applying a station once, the merge rules and why applying only adds, applying a newer version, `stations:` in the profile, `the-cook station apply`, `list` and `show` |
| `wrapper` | `cook`, `.cook/wrapper.yaml` as the only pin, the cache, checksum and signature verification, `--no-download`, global-install deferral, upgrades, air-gapped machines |
| `safety` | the threat model, the layers of defence, how to place a policy entry, unattended runs, actions as code, secrets, attempt actions and prompt injection |
| `pitfalls` | known pitfalls, gaps and traps, each with its symptom, cause and what to do |

## Reference

```text
the-cook explain <topic>
```

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Unknown topic; the valid topics are listed |

## Examples

```sh
./cook explain policy | less
./cook explain invariants
```

A prompt for a coding agent that works well:

```text
Allow workers to run the deploy status task in this repository. Read `./cook explain policy` first.
```

## Guidelines

- Do point workers at `explain` or the skill instead of pasting rules into prompts; the text stays current with the
  binary.
- Don't copy `explain` output into `AGENTS.md`: it would go stale on the next release. The
  [managed blocks](render.md#managed-blocks) `the-cook render` writes into `AGENTS.md`, such as `commands`, `layout` and
  `policy`, already carry the repository-specific facts.
