# 0009. Harness plugins render agent-neutral sources into each agent's native files

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
- Configuration maintained by hand once per agent drifts. Example: `acme/api` writes its permissions by hand twice, in
  Claude Code's settings and in Codex CLI's rule files. A teammate allows a new test command in one and forgets the
  other, so the two agents disagree, and the repository grows a dedicated check whose only job is to compare the two
  files and keep them in step.
- Agents change their formats between releases. A central description fixes such a change once, and is broken by it in
  every repository until fixed.
- What an agent needs from the tool can be described as data (Context of [0022](0022-tool-integration.md)): the native
  files and keys to own, a template for each, the paths in its hook payload, and the arguments that start it.
- Some constructs exist in one agent only. A repository can hook events that only some agents emit, and agents differ in
  whether they report that they are waiting for a person or that a turn ended.
- Repositories assign agents to roles differently. Example: `acme/api` uses Claude Code interactively and Codex CLI only
  for unattended work, `acme/cli` uses both interactively, and `acme/web` uses Claude Code for both.

## Decision

1. The sources are agent-neutral: one permission policy and one set of hook declarations per repository
   ([0006](0006-repository-contract.md)).
2. Each agent is served by a harness plugin ([0022](0022-tool-integration.md)) that fills the harness contract
   ([0023](0023-core-and-contracts.md)): its renders turn the neutral sources into that agent's native files under the
   ownership rules of [0008](0008-native-configuration.md), its operations say how the agent is started and how its
   hook payload is read, and its checks declare the agent versions it supports.
3. Each harness plugin maps its agent's hook payloads onto one neutral event set. The set includes the agent's
   notification events, waiting for a person and turn ended ([0019](0019-action-kinds.md)), and the points where
   guidance enters the agent's context ([0020](0020-standing-context.md)). Where an agent lacks an event, the plugin
   renders nothing for it and the health check, the tool's read-only report on a repository's setup, reports the gap.
4. The harness contract is public and documented. Adding an agent means adding a harness plugin, shipped in a release
   or declared by a repository from an outside source; the sources and the other plugins do not change.
5. A construct the neutral model does not express may be written as a raw, agent-specific entry. A plugin whose agent
   cannot express it skips it. Every native permission rule of every agent the release ships a plugin for is
   expressible this way ([0016](0016-permission-adoption.md)).
6. Which agent fills which role in a repository, interactive or unattended, is repository data, not plugin content.

## Reasons

- **1.** One source means one change reaches every agent with equivalent effect. The alternative, one hand-written
  file per agent, drifts. The dedicated check of the `acme/api` example in Context shows the cost.
- **2.** Each agent's syntax, schema and release cadence is confined to one plugin. A format change in one agent is
  fixed in one plugin, and the other agents' output is untouched. As data, the plugin can be fixed without a change to
  the binary's code, and a team can describe an agent the project does not ship ([0022](0022-tool-integration.md)).
  The supported-version declaration lets the health check stop an unsupported agent version before the tool renders
  configuration that agent misreads. The trade: everything an agent needs must fit the contract's fixed operations.
- **3.** Hook declarations, notification actions and context triggers are written once, against neutral events.
  Without normalisation, each would be declared once per agent and drift like the rest. Rendering nothing for a
  missing event, and saying so, tells a person which agent lacks a behaviour instead of letting them assume it works.
- **4.** New agents keep appearing. A public contract lets one be supported without touching the data every adopting
  repository has committed ([0006](0006-repository-contract.md)) or the schema it follows
  ([0005](0005-schema-compatibility.md)).
- **5.** The neutral model always lags the agents. Without a passthrough, a repository whose rules or hooks fall
  outside the model cannot adopt without losing them, which [0016](0016-permission-adoption.md) forbids. Skipping a
  raw entry for agents that cannot express it keeps every other plugin's output valid.
- **6.** Role assignments differ between repositories. A plugin that fixed one assignment would impose it on every
  repository. With the assignment as data, each repository states its own.

## Rejected alternatives

### One source per agent

At its best, each agent's full feature set is available with no translation. It loses because every rule exists once per
agent, and those copies drift, as the `acme/api` example in Context shows.

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
- A breaking agent release affects every adopting repository until a plugin update ships; the supported-version
  declaration detects it but does not prevent it.
- A behaviour declared once is absent for agents that lack its event.

Must be maintained:

- One shipped plugin per supported agent, its supported-version range, its contract tests, the harness contract and
  the neutral event set.

## Confirmation

- Every shipped harness plugin passes a shared contract suite: the same policy yields the same allow, ask or deny
  outcome for the same fixture commands in each agent's rendered output.
- Recorded hook payloads from each agent, including notification and context-injection events, normalise to the same
  neutral events.
- A fixture whose agent lacks a declared event renders nothing for it, and the health check reports the gap.
- The health check reports agent versions outside a plugin's supported range.
