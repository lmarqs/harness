# 0002. Deterministic work lives in the binary; judgement is delegated to agents guided by shipped documentation

- Status: Accepted
- Date: 2026-10-09

## Context

Two kinds of work are involved in giving a repository a consistent agent setup:

- Work with one correct output for given inputs: rendering configuration from data, detecting drift, recording
  ownership, removing what was written, enforcing invariants.
- Work that needs judgement: interpreting hand-written configuration whose intent is not machine-readable, writing
  project prose, choosing policy entries, reconciling conflicting names.

Forces:

- Propagation without an engine produces different output for the same input. The existing workflow was ported
  between repositories by hand; every copy became a dialect, and two of three lost the safety settings. An agent
  following a scaffolding skill has the same property, because nothing fixes its output.
- Encoding judgement in code produces heuristics that fail on the first repository they did not anticipate. A survey
  of 51 repositories found three different shapes of the same skills directory, instruction files with 2 to 24 KB of
  hand-written content, and hooks on events no shared model covered.
- Agents working inside a repository need the rules and their reasons to change that repository's setup correctly.

## Decision

The binary performs only work whose output is fully determined by its inputs, and performs it identically on every
machine: the same input bytes produce the same output bytes, and a second run changes nothing. The binary never
writes prose, memory, domain skills or policy choices.

Work that needs judgement belongs to an agent or a person. The binary ships the documentation an agent needs (the
rules, their reasons and the file contracts) embedded in itself, both as a skill installed into the repository and
as printable documentation. It hands over unfinished work as a machine-readable list of unresolved items. The agent
edits the declarative sources ([0006](0006-repository-state-is-declarative-data.md)); the binary renders and checks.

When the binary meets input it cannot map deterministically, it leaves that input untouched and lists it as
unresolved. It never guesses.

## Alternatives considered

### Everything in the binary

At its best, one command completes every adoption with no agent and no person. Not chosen: it requires heuristics
over unbounded input shapes; a wrong guess silently rewrites a team's configuration; and generated prose is generic
where a repository needs specific prose.

### Everything by agents, no engine

A skill describes the target setup and an agent ports it to each repository. At its best, there is no code to
maintain and every repository gets a tailored result. Not chosen: output differs per run, nothing enforces the
invariants, and no drift check exists. Hand propagation, which shares these properties, produced three dialects
and lost safety settings.

## Consequences

Positive:

- Identical inputs give byte-identical outputs in every repository; a fix reaches all of them in one release.
- Agents get the reasoning, not only the rules, inside the repository they work in.

Negative:

- Adopting a repository with hand-written configuration takes two steps: a deterministic pass, then an agent or a
  person resolving the listed items.
- The embedded documentation is part of the product; when it disagrees with the binary, agents act on wrong rules.

Must be maintained:

- The boundary itself: each new capability is classified as deterministic or judgement before it is built.
- The format of the unresolved-items list, which agents and scripts consume.

## Confirmation

- CI renders the same fixtures on Linux and macOS and compares outputs byte for byte; a second render produces no change.
- Fixture tests assert that unrecognised input is left unchanged and appears in the unresolved-items list.
- Review rejects any code path that writes to knowledge files ([0011](0011-built-in-safety-invariants.md)).
