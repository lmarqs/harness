# explain

## Objective

**Problem.** A developer or an agent about to edit a repository's permissions, hooks or settings, or puzzled by a
refusal, needs the rules and the reasons behind them for the exact release the repository runs. Documentation on the web
may describe another release or be out of reach offline, and rules pasted into prompts and instruction files go stale
with the next release. A person and an agent who read different texts end up following different rules.

**Goal.** Anyone working in a repository, online or offline, can read the rules and their reasons as they hold for the
release that repository runs, and a person and an agent asking the same question get the same answer.

## Overview

`the-cook explain <topic>` prints the built-in documentation for one topic: what the standard is, and the reasoning
behind it. It is the same text the [shipped skill](skills.md#the-shipped-skill) carries, so a person at a terminal and
an agent in a session learn the same rules.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## When to use it

- An agent must edit `.cook/policy.yaml`, `.cook/hooks.yaml` or the profile correctly: `explain policy`,
  `explain hooks`, `explain profile`.
- An agent is finishing an adoption, that is, resolving the to-do `the-cook scaffold --adopt` leaves after it takes over
  a hand-written agent setup ([scaffold](scaffold.md#adopt)): `explain adoption`.
- Someone asks why `the-cook` refuses something: `explain invariants`, `explain safety`.
- An agent declares or triggers lifecycle actions, or a notification: `explain lifecycle`, `explain hooks`.
- Someone wants a stack's starting commands, [lanes](verify.md) and hooks, or reads a line such as
  `kept lanes.unit: differs from recipe node` from `recipe apply`: `explain recipes`.
- Someone asks how `the-cook` works with git, an agent or another program, or wants to use a program the release ships
  no recipe for: `explain recipes`.
- Someone wants a directive or a skill applied without invoking it each time: `explain context`.
- Someone connects a loop runner or a task tracker: `explain worker`, `explain lifecycle`, `explain recipes`.
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
| `profile` | `.cook/config.yaml` fields and how to choose values |
| `policy` | `.cook/policy.yaml`, the three entry forms, rendering per agent and per spelling of a `the-cook` command, precedence, the floor rules of the recipes in use, how to place an entry |
| `hooks` | `.cook/hooks.yaml`, the hook runtime, the `{file}` placeholder, notification hooks (`attention`, `turn_end`), the hook command and why it never downloads |
| `lifecycle` | lifecycle events including `blocked`, trigger points, scopes, task binding with `workspace create --task`, the action kinds `run`, `http` and `agent`, the built-in guards, the `COOK_*` environment, `lifecycle.jsonl` in the state directory |
| `context` | standing context: `always` and `when` entries, triggers per agent, `content` and `pointer`, roles, `static` entries, trusted sources, the budget, `the-cook context show` |
| `adoption` | adoption classes, import scopes, the equivalence check, the to-do, how an agent finishes an adoption |
| `verify` | lanes, skip reasons, the receipt and its freshness rules |
| `worker` | `the-cook worker`: the worker contract and its tamper check, rules read from the base commit, the replay, `--commit` and `--withhold`, its exit codes, how a loop runner names it as its agent command, who fires the events of a run |
| `invariants` | the invariants built into the binary, the floor rules they apply from recipes, and why none can be turned off |
| `blocks` | managed blocks, their markers, the per-module and per-recipe ids, convention blocks and tool blocks, the `AGENTS.md` budget, the editing rules |
| `recipes` | recipes and their parts (entries, templates, renders, operations, checks), the version-control and harness contracts, floor rules, the builtin recipes and what each holds, fetched recipes and their hash pin, `recipes:` and `vcs:` in the profile, `the-cook recipe list`, `show`, `apply` and `fetch`, the merge rules, why applying only adds, applying again after an upgrade |
| `wrapper` | `cook`, `.cook/wrapper.yaml` as the only pin, the cache, checksum and signature verification, `--no-download`, global-install deferral, upgrades, air-gapped machines |
| `safety` | the threat model, the layers of defence, how to place a policy entry, unattended runs, actions as code, secrets, agent actions and prompt injection |
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

An agent prompt that works well:

```text
Allow agents to run the deploy status task in this repository. Read `./cook explain policy` first.
```

## Guidelines

- Do point agents at `explain` or the skill instead of pasting rules into prompts; the text stays current with the
  binary.
- Don't copy `explain` output into `AGENTS.md`: it would go stale on the next release. The
  [managed blocks](render.md#managed-blocks) `the-cook render` writes into `AGENTS.md`, such as `commands`, `layout` and
  `policy`, already carry the repository-specific facts.
