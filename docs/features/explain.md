# explain

## Objective

**Problem.** A developer or an agent about to edit a repository's permissions, hooks or settings, or puzzled by a
refusal, needs the rules and the reasons behind them for the exact release the repository runs. Documentation on the web
may describe another release or be out of reach offline, and rules pasted into prompts and instruction files go stale
with the next release. A person and an agent who read different texts end up following different rules.

**Goal.** Anyone working in a repository, online or offline, can read the rules and their reasons as they hold for the
release that repository runs, and a person and an agent asking the same question get the same answer.

## Overview

`harness explain <topic>` prints the built-in documentation for one topic: what the standard is, and the reasoning
behind it. It is the same text the [harness skill](skills.md#the-harness-skill) carries, so a person at a terminal and
an agent in a session learn the same rules.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## When to use it

- An agent must edit `.harness/policy.yaml`, `.harness/hooks.yaml` or the profile correctly: `explain policy`,
  `explain hooks`, `explain profile`.
- An agent is finishing an adoption, that is, resolving the to-do `harness scaffold --adopt` leaves after it takes over
  a hand-written agent setup ([scaffold](scaffold.md#adopt)): `explain adoption`.
- Someone asks why `harness` refuses something: `explain invariants`, `explain safety`.
- An agent declares or triggers lifecycle actions, or a notification: `explain lifecycle`, `explain hooks`.
- Someone wants a stack's starting commands, [lanes](verify.md) and hooks, or reads a line such as
  `kept lanes.unit: differs from preset node` from `preset apply`: `explain presets`.
- Someone wants a directive or a skill applied without invoking it each time: `explain context`.
- Someone asks how the repository pins, downloads and verifies the release it runs: `explain wrapper`.
- Something behaves unexpectedly: `explain pitfalls`.
- You are offline and need the reference for the pinned release: the text is embedded in the binary.

## How it works

The text is embedded in the binary and matches its version. Through `./harnessw`, that binary is the release the
repository pins in `.harness/wrapper.yaml`. `explain` reads nothing from the repository and writes nothing. Output is
Markdown on stdout.

## Topics

| Topic | Explains |
| --- | --- |
| `layout` | the `.harness/` and `.agents/` directories and who owns each, `harnessw`, native directories, links, the state paths |
| `profile` | `.harness/config.yaml` fields and how to choose values |
| `policy` | `.harness/policy.yaml`, the three entry forms, rendering per agent and per spelling of a `harness` command, precedence, the built-in base, how to place an entry |
| `hooks` | `.harness/hooks.yaml`, the hook runtime, the `{file}` placeholder, notification hooks (`attention`, `turn_end`), the hook command and why it never downloads |
| `lifecycle` | lifecycle events including `blocked`, trigger points, scopes, the action kinds `run`, `http` and `agent`, the built-in guards, the `HARNESS_*` environment, `lifecycle.jsonl` in the state directory |
| `context` | standing context: `always` and `when` entries, triggers per agent, `content` and `pointer`, roles, `static` entries, trusted sources, the budget, `harness context show` |
| `adoption` | adoption classes, import scopes, the equivalence check, the to-do, how an agent finishes an adoption |
| `verify` | lanes, skip reasons, the receipt and its freshness rules |
| `ralph` | the coordinator, the worker contract and its tamper check, managed and inherited configuration, the embedded prompt, delivery after a run |
| `invariants` | the built-in invariants and why none can be turned off |
| `blocks` | managed blocks, their markers, the per-module ids, convention blocks and tool blocks, the `AGENTS.md` budget, the editing rules |
| `presets` | the built-in stack presets and what each holds, `harness preset list`, `show` and `apply`, the merge rules, why applying only adds and records nothing, applying again after an upgrade |
| `wrapper` | `harnessw`, `.harness/wrapper.yaml` as the only pin, the cache, checksum and signature verification, `--no-download`, global-install deferral, upgrades, air-gapped machines |
| `safety` | the threat model, the layers of defence, how to place a policy entry, unattended runs, actions as code, secrets, agent actions and prompt injection |
| `pitfalls` | known pitfalls, gaps and traps, each with its symptom, cause and what to do |

## Reference

```text
harness explain <topic>
```

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Unknown topic; the valid topics are listed |

## Examples

```sh
./harnessw explain policy | less
./harnessw explain invariants
```

An agent prompt that works well:

```text
Allow agents to run the deploy status task in this repository. Read `./harnessw explain policy` first.
```

## Guidelines

- Do point agents at `explain` or the skill instead of pasting rules into prompts; the text stays current with the
  binary.
- Don't copy `explain` output into `AGENTS.md`: it would go stale on the next release. The
  [managed blocks](render.md#managed-blocks) `harness render` writes into `AGENTS.md`, such as `commands`, `layout` and
  `policy`, already carry the repository-specific facts.
