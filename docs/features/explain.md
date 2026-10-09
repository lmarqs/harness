# explain

`harness explain <topic>` prints the built-in documentation for one topic: what the standard is, and the reasoning
behind it. It is the same text the [harness skill](skills.md#the-harness-skill) carries, so a person at a terminal and
an agent in a session learn the same rules.

## When to use it

- An agent must edit `.agents/policy.yaml`, `.agents/hooks.yaml` or the profile correctly: `explain policy`,
  `explain hooks`, `explain profile`.
- An agent is finishing an adoption: `explain adoption`.
- Someone asks why `harness` refuses something: `explain invariants`, `explain safety`.
- An agent declares or triggers lifecycle actions: `explain lifecycle`.
- Something behaves unexpectedly: `explain pitfalls`.
- You are offline and need the reference for the installed version: the text is embedded in the binary.

## How it works

The text is embedded in the binary and matches its version. `explain` reads nothing from the repository and writes
nothing. Output is Markdown on stdout.

## Topics

| Topic | Explains |
| --- | --- |
| `layout` | the `.agents/` layout, native directories, links, state roots |
| `profile` | `.agents/harness.yaml` fields and how to choose values |
| `policy` | `.agents/policy.yaml`, the three entry forms, rendering per agent, precedence, the built-in base, how to place an entry |
| `hooks` | `.agents/hooks.yaml`, the hook runtime, the `{file}` placeholder, the missing-binary guard |
| `lifecycle` | lifecycle events, trigger points, scopes, declared actions, the built-in guards, the `HARNESS_*` environment, `.work/lifecycle.jsonl` |
| `adoption` | adoption classes, import scopes, the equivalence check, the to-do, how an agent finishes an adoption |
| `verify` | lanes, skip reasons, the receipt and its freshness rules |
| `ralph` | the coordinator, the worker contract and its tamper check, managed and inherited configuration, the embedded prompt, delivery after a run |
| `invariants` | the built-in invariants and why none can be turned off |
| `blocks` | managed blocks, their ids and the editing rules |
| `presets` | stack presets, `extends` sources, `@<ref>` pinning and recorded commits |
| `safety` | the threat model, the layers of defence, how to place a policy entry, unattended runs, lifecycle actions as code |
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
harness explain policy | less
harness explain invariants
```

An agent prompt that works well:

```text
Allow agents to run the deploy status task in this repository. Read `harness explain policy` first.
```

## Guidelines

- Do point agents at `explain` or the skill instead of pasting rules into prompts; the text stays current with the
  binary.
- Don't copy `explain` output into `AGENTS.md`: it would go stale on the next release. The `commands` and
  `policy-summary` blocks already carry the repository-specific facts.
