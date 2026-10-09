# 0009. Agent-specific configuration is produced by adapters from agent-neutral sources

- Status: Accepted
- Date: 2026-10-09

## Context

- The supported agents differ in permission syntax (pattern strings in one, rule files in another), in hook schema
  and in the payload they send to a hook. More agents exist and new ones appear.
- Hand-maintained configuration per agent drifted. One copy of the existing workflow needed a dedicated check that
  compared the two agents' hand-written files to keep them in step.
- Agents change their formats between releases. A central engine fixes such a change once, and is also broken by
  it everywhere until fixed.
- Some constructs exist in one agent only. Surveyed repositories hold hooks on events that only some agents emit.

## Decision

- The sources are agent-neutral: one permission policy and one set of hook declarations per repository
  ([0006](0006-repository-state-is-declarative-data.md)).
- Each agent is served by an adapter that renders the neutral sources into that agent's native files under the
  merge rules of [0008](0008-merge-rendered-native-configuration.md), normalises that agent's hook payloads into
  one neutral event, and declares the agent versions it supports.
- The adapter contract is public and documented. Adding an agent means adding an adapter; sources and other
  adapters do not change.
- A construct the neutral model does not express may be written as a raw, agent-specific entry. An adapter whose
  agent cannot express it skips it. Every native permission rule of every supported agent is expressible this way
  ([0016](0016-lossless-adoption-of-native-permissions.md)).
- Which agent fills which role in a repository, interactive or unattended, is repository data, not adapter code.

## Alternatives considered

### One source per agent

At its best, each agent's full feature set is available. Not chosen: every rule exists once per agent, and the
copies drifted in practice.

### A neutral model with no passthrough

At its best, the contract is smallest. Not chosen: repositories with hooks the model does not cover could not adopt
without losing them.

### One agent's format as the source, translated for the others

At its best, no new format is introduced. Not chosen: it ties the contract to one vendor's syntax and release
schedule.

## Consequences

Positive:

- One policy change reaches every agent with equivalent effect.
- Supporting a new agent is one adapter.

Negative:

- The neutral model must follow the agents as they evolve, and translation of agent-specific features can lose
  meaning.
- A raw entry takes effect only for the agents that can express it.
- A breaking agent release affects every adopting repository until an adapter update ships; the supported-version
  declaration detects it but does not prevent it.

Must be maintained:

- One adapter per agent, its supported-version table, and its contract tests.

## Confirmation

- Every adapter passes a shared contract suite: the same policy yields the same allow, ask or deny outcome for the
  same fixture commands in each agent's rendered output.
- Recorded hook payloads from each agent normalise to the same neutral event.
- The tool's health check reports agent versions outside an adapter's supported range.
