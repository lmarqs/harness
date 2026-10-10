# 0002. The binary does only deterministic work

## Objective

**Problem.** Setting up agents in a repository mixes work that has one right answer with work that needs judgement. Done
by hand or by an agent, the mechanical part comes out different every time, so repositories diverge and nothing can
check them. Done by a program, the judgement part breaks on the first input nobody foresaw.

**Goal.** Mechanical setup work gives the same checkable result in every repository, and judgement stays with the people
and agents who can exercise it.

## Context

Giving a repository a consistent agent setup involves two kinds of work:

- work with one correct output for given inputs: rendering configuration from data, detecting drift, recording
  ownership, removing what was written, enforcing invariants;
- work that needs judgement: interpreting hand-written configuration whose intent is not machine-readable, writing
  project prose, choosing policy entries, reconciling conflicting names.

Facts:

- Propagation that nothing fixes produces different output for the same input. Example: acme copies one agent workflow
  by hand from `acme/api` into `acme/web` and `acme/infra`; each copy becomes its own dialect, and two of them start
  unattended agents with permission checks turned off. An agent following a scaffolding skill has the same property,
  because nothing fixes its output: run twice on one repository, it can write two different setups.
- Input shapes are unbounded. Example: at acme, `.claude/skills` is a directory of the team's own skills in `acme/api`,
  a symbolic link to a shared directory in `acme/web` and absent in `acme/infra`; `AGENTS.md` runs from a few lines to
  tens of kilobytes of hand-written prose; and some repositories hook events that no shared model covers.
- A drift check compares a repository with a fresh render; it is meaningful only if the render is reproducible.
- Agents working inside a repository change its setup correctly only when they know the rules, the reasons and which
  files are generated.

## Decision

1. The binary performs only work whose output is fully determined by its inputs. The same input bytes produce the same
   output bytes on every machine, and a second run changes nothing.
2. The binary never writes prose, memory, domain skills or policy choices. Work that needs judgement belongs to an agent
   or a person, who edits the declarative sources ([0006](0006-repository-contract.md)); the binary renders and checks
   the result.
3. The binary embeds the documentation an agent needs (the rules, their reasons and the file contracts) and ships it
   both as a skill written into the repository and as printable documentation.
4. When the binary meets input it cannot map deterministically, it leaves the input untouched and lists it as
   unresolved in a machine-readable list. It never guesses.

## Reasons

- **1.** Determinism lets one fix reach every repository with identical effect, and it is what makes the drift check
  meaningful: a check against a non-reproducible render fails on its own. Idempotence makes a re-run safe in CI and
  across many repositories. The trade: anything whose correct output depends on intent is outside the binary.
- **2.** Heuristics over unbounded inputs fail on the first repository they did not anticipate, and a wrong guess
  silently rewrites a team's configuration. Prose, memory and policy choices are specific to a repository; generated
  prose is generic where a repository needs specifics. Because judgement lands in the declarative sources, the binary
  still renders and checks it, so judgement never bypasses the deterministic path.
- **3.** An agent acts correctly only when the rules and their reasons are present in the repository it works in.
  Embedding them in the binary keeps them in step with the release that enforces them, which is the release the
  repository pins ([0017](0017-engine-version.md)). The trade: the documentation becomes part of the product, and an
  error in it misleads agents.
- **4.** Listing instead of guessing turns the boundary into a hand-off: the binary does the mechanical part and hands
  the remainder over as data that an agent or a script consumes. Leaving the input untouched preserves it for whoever
  resolves it.

## Rejected alternatives

### Everything in the binary

At its best, one command completes every adoption with no agent and no person. It loses because it needs heuristics
over unbounded input shapes, a wrong guess silently rewrites a team's configuration, and generated prose is generic
where a repository needs specific prose.

### Everything by agents, with no engine

A skill describes the target setup and an agent ports it to each repository. At its best, there is no code to maintain
and every repository gets a tailored result. It loses because output differs per run, nothing enforces the invariants
and no drift check exists. Hand propagation has the same properties: it turns the acme workflow in Context into three
dialects, two of which lose their safety settings.

## Consequences

Costs:

- Adopting a repository with hand-written configuration takes two steps: a deterministic pass, then an agent or a person
  resolving the listed items.
- When the embedded documentation disagrees with the binary, agents act on wrong rules.

Must be maintained:

- The boundary itself: each new capability is classified as deterministic or judgement before it is built.
- The format of the unresolved-items list, which agents and scripts consume.

## Confirmation

- CI renders the same fixtures on Linux and macOS and compares the outputs byte for byte; a second render produces no
  change.
- Fixture tests assert that unrecognised input is left unchanged and appears in the unresolved-items list.
- Review rejects any code path that writes knowledge files ([0011](0011-safety-invariants.md)).
