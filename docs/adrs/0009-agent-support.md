# 0009. Adapters render agent-neutral sources into each agent's native files

## Objective

**Problem.** Each agent has its own permission syntax, hook schema and events, and new agents keep appearing.
Configuration kept by hand once per agent drifts, so one repository grants different permissions to different agents,
and supporting another agent means writing everything again.

**Goal.** One declaration grants the same permissions and runs the same hooks in every supported agent, and supporting
another agent changes no repository's declarations.

## Context

- The supported agents differ in permission syntax (pattern strings in Claude Code's settings, rule files that match
  the leading words of a command in Codex CLI), in hook schema, in the payload they send to a hook, and in the events
  they emit. More agents exist and new ones appear.
- Configuration maintained by hand once per agent drifts. One hand-ported copy of an agent workflow carries a
  dedicated check whose only job is to compare the two agents' hand-written files and keep them in step.
- Agents change their formats between releases. A central engine fixes such a change once, and is broken by it in
  every repository until fixed.
- Some constructs exist in one agent only. Surveyed repositories hold hooks on events that only some agents emit, and
  agents differ in whether they report that they are waiting for a person or that a turn ended.
- Repositories assign agents to roles differently: one uses an agent interactively and another only for unattended
  work; another uses one agent for both.

## Decision

1. The sources are agent-neutral: one permission policy and one set of hook declarations per repository
   ([0006](0006-repository-contract.md)).
2. Each supported agent is served by an adapter that renders the neutral sources into that agent's native files under
   the ownership rules of [0008](0008-native-configuration.md), and declares the agent versions it supports.
3. Each adapter normalises its agent's hook payloads into one neutral event set. The set includes the agent's
   notification events, waiting for a person and turn ended ([0019](0019-action-kinds.md)), and the points where
   guidance enters the agent's context ([0020](0020-standing-context.md)). Where an agent lacks an event, the adapter
   renders nothing for it and the health check reports the gap.
4. The adapter contract is public and documented. Adding an agent means adding an adapter; the sources and the other
   adapters do not change.
5. A construct the neutral model does not express may be written as a raw, agent-specific entry. An adapter whose
   agent cannot express it skips it. Every native permission rule of every supported agent is expressible this way
   ([0016](0016-permission-adoption.md)).
6. Which agent fills which role in a repository, interactive or unattended, is repository data, not adapter code.

## Reasons

- **1.** One source means one change reaches every agent with equivalent effect. The alternative, one hand-written
  file per agent, drifts; the workflow copy that needs a check to keep two agents' files in step shows the cost.
- **2.** Each agent's syntax, schema and release cadence is confined to one component. A format change in one agent
  is fixed in one adapter, and the other agents' output is untouched. The supported-version declaration lets the
  health check stop an unsupported agent version before the tool renders configuration that agent misreads.
- **3.** Hook declarations, notification actions and context triggers are written once, against neutral events.
  Without normalisation, each would be declared once per agent and drift like the rest. Rendering nothing for a
  missing event, and saying so, tells a person which agent lacks a behaviour instead of letting them assume it works.
- **4.** New agents keep appearing. A public contract lets one be supported without touching the data every adopting
  repository has committed ([0006](0006-repository-contract.md)) or the schema it follows
  ([0005](0005-schema-compatibility.md)).
- **5.** The neutral model always lags the agents. Without a passthrough, a repository whose rules or hooks fall
  outside the model cannot adopt without losing them, which [0016](0016-permission-adoption.md) forbids. Skipping a
  raw entry for agents that cannot express it keeps every other adapter's output valid.
- **6.** Role assignments differ between repositories. An adapter that fixed one assignment would impose it on every
  repository; as data, each repository states its own.

## Rejected alternatives

### One source per agent

At its best, each agent's full feature set is available with no translation. It loses because every rule exists once
per agent, and those copies drift in practice.

### A neutral model with no passthrough

At its best, the contract is the smallest possible and every entry means the same in every agent. It loses because
repositories with hooks or rules outside the model cannot adopt without losing them.

### One agent's format as the source, translated for the others

At its best, no new format is introduced and one agent's users already know it. It loses because it ties the
repository-facing contract to one vendor's syntax and release schedule, and every other agent's output depends on
translating a format designed for someone else.

## Consequences

Costs:

- The neutral model must follow the agents as they evolve, and translating agent-specific features can lose meaning.
- A raw entry takes effect only for the agents that can express it.
- A breaking agent release affects every adopting repository until an adapter update ships; the supported-version
  declaration detects it but does not prevent it.
- A behaviour declared once is absent for agents that lack its event.

Must be maintained:

- One adapter per agent, its supported-version table, its contract tests, and the neutral event set.

## Confirmation

- Every adapter passes a shared contract suite: the same policy yields the same allow, ask or deny outcome for the
  same fixture commands in each agent's rendered output.
- Recorded hook payloads from each agent, including notification and context-injection events, normalise to the same
  neutral events.
- A fixture whose agent lacks a declared event renders nothing for it, and the health check reports the gap.
- The health check reports agent versions outside an adapter's supported range.
