# 0001. The tool is a meta-harness: it configures agent harnesses and never acts as one

- Status: Accepted
- Date: 2026-10-09

## Context

An agent harness is the program that runs a model in a loop with tools: an interactive coding agent, or an
unattended runner that drives an agent through a list of tasks. Each harness reads its permissions, hooks,
instructions and skills from files in the repository.

Forces:

- The problem is the consistency of harness configuration across many repositories, not the capability of the
  agents. In the evidence behind this project, one agent workflow was hand-ported to two more repositories within
  ten days; each port became a different dialect, and two of the three copies run unattended agents with
  permission-bypass flags that the original refuses.
- Harnesses are products developed by their vendors and change frequently. A tool that reimplements an agent loop
  competes with them and must follow every model and API change they already follow.
- Calling a model requires credentials, a provider choice, a cost budget and data-handling rules. Each of these is
  an organization's decision, not a tool's.
- Configuration a harness reads from committed files is reviewable in a pull request. Behaviour inside a wrapper
  process is not.

## Decision

The tool is a meta-harness. It writes, checks and removes the configuration that harnesses read, and it constrains
how harnesses are started. It never calls a model API, holds no model credentials, contains no agent loop and makes
no decision that requires a model.

When unattended operation needs a harness to run, the tool starts the harness as a child process with arguments
and environment it controls, and judges the outcome only by deterministic checks
([0011](0011-built-in-safety-invariants.md)). The reasoning happens inside the harness.

## Alternatives considered

### An agent: the tool calls models and runs its own loop

At its best, one program controls prompts, tools, permissions and verification end to end, with no dependency on
third-party configuration formats. Not chosen: it competes with harnesses teams already use, takes on provider
credentials and cost, must track model and API changes that harness vendors already track, and removes the
option of keeping a preferred harness.

### A wrapper: every agent invocation goes through the tool

At its best, the wrapper sees every session and enforces policy at runtime without relying on each harness's
permission model. Not chosen: harnesses already enforce permissions from their configuration files; a wrapper
makes the tool a runtime dependency of every session (contrary to [0010](0010-optional-and-transparent-adoption.md)),
moves policy from reviewable files into process logic, and has no effect when a person starts the harness directly.

## Consequences

Positive:

- The tool's scope is bounded by the harnesses' configuration surfaces, and its outputs are files a reviewer can read.
- People keep the harnesses they choose, started however they choose.
- The tool has no model credentials, no traffic to model providers and no token cost.

Negative:

- Enforcement is as strong as each harness's own permission and hook model; the tool cannot stop an action the
  harness does not expose.
- A change to a harness's configuration format breaks the tool's output for that harness until the tool is updated
  ([0009](0009-agent-neutral-sources-and-adapters.md)).

Must be maintained:

- Knowledge of each supported harness's configuration surface across its versions.

## Confirmation

- Review rejects any dependency on a model-provider SDK and any code path that sends a prompt to a model.
- The tool's test suite runs without model credentials and without network access to model providers.
