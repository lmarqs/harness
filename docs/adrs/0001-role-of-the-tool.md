# 0001. The tool configures agent harnesses and never acts as one

## Objective

**Problem.** Agent harness configuration is copied by hand from repository to repository, and the copies drift into
dialects that lose safety settings. A tool that fixes this has to know where its job ends. Without a boundary it grows
into a second agent harness, chasing every model and API change the harness vendors already follow and holding
credentials and costs that belong to each organization.

**Goal.** Harness configuration stays consistent across every repository, while agents keep the capabilities, model
access and credentials their vendors and organizations give them.

## Context

An agent harness is the program that runs a model in a loop with tools: an interactive coding agent, or an unattended
runner that drives an agent through a list of tasks. Each harness reads its permissions, hooks, instructions and skills
from files in the repository.

- Example: acme copies one agent workflow by hand from `acme/api` into `acme/web` and `acme/infra`. A year later each
  copy is its own dialect. `acme/web` added a lint step and skips permission prompts through its unattended runner's
  configuration, `acme/infra` starts the second agent it uses for unattended work with a permission-bypass flag, and
  `acme/api` refuses both. All three run the same harnesses and models: the copies differ in what they write and how
  they start agents, not in what the agents can do.
- Harnesses are products their vendors develop and change often. A program that reimplements an agent loop has to follow
  every model and API change the harness vendors already follow.
- Calling a model requires credentials, a provider choice, a cost budget and data-handling rules. Each of these is an
  organization's decision.
- Agent tool servers (the Model Context Protocol, MCP) each carry their own connection and credentials. Harnesses
  already hold these connections, decide when a model uses them and put each use under their permission model.
- Harnesses enforce permissions from their configuration files, and a person can always start a harness directly.
- Configuration a harness reads from committed files is reviewable in a pull request. Behaviour inside a long-running
  process is not.

## Decision

1. The tool writes, checks and removes the configuration that agent harnesses read, and constrains how a harness starts
   when the tool starts it.
2. The tool never calls a model API, holds no model credentials, contains no agent loop and makes no decision that
   needs a model.
3. The tool never acts as a client of agent tool servers. Work that needs such a tool is done by a harness that owns the
   connection and its credentials ([0019](0019-action-kinds.md)).
4. When unattended work or a declared action needs a harness, the tool starts it as a child process with arguments and
   environment the tool controls, and judges the outcome only by deterministic checks
   ([0011](0011-safety-invariants.md)).

## Reasons

- **1.** Hand-copied setups diverge in their configuration and their start-up arguments, as the acme copies in Context
  do, so owning those surfaces is what solves it. The output is committed files, which reviewers read as diffs and which
  keep working when the tool is absent ([0010](0010-dependence-on-the-binary.md)). The trade: the tool can enforce only
  what a harness's configuration surface exposes.
- **2.** A harness already follows model and API changes. A second loop duplicates that work and competes with the
  harness a team chose. Credentials, provider choice and cost belong to organizations. A tool that holds none of them
  has no token cost, sends no data to a model provider and needs no data-handling review. The trade: every capability
  that needs reasoning comes from a harness, never from the tool.
- **3.** A tool-server connection carries credentials and grants actions that a model chooses at run time. A client in
  the tool would duplicate credentials the harness already holds, embed a protocol client in the binary, and make the
  tool choose which remote tool to call, which is a model's decision (point 2). Integrations that need no judgement
  reach a service through its HTTP API or command-line client instead ([0019](0019-action-kinds.md)). The trade: work
  available only as a tool-server tool needs an agent run, with its cost and its variance.
- **4.** Unattended runs are where a hand-copied setup loses its safety settings: a permission prompt stalls a run that
  nobody watches, so a copy turns the prompts off in the start-up arguments or the runner's configuration, as two of the
  three acme copies in Context do. The tool therefore controls the arguments, permission mode and environment of every
  harness it starts. It judges the outcome by checks it can replay, because an agent can claim completion that did not
  happen ([0011](0011-safety-invariants.md)). Starting the harness as an unmodified child process keeps it replaceable
  and keeps its reasoning inside it.

## Rejected alternatives

### An agent: the tool calls models and runs its own loop

At its best, one program controls prompts, tools, permissions and verification end to end, with no dependency on
third-party configuration formats. It loses because it competes with the harnesses teams already use, takes on provider
credentials and cost, must track model and API changes that harness vendors already track, and removes the option of
keeping a preferred harness.

### A runtime proxy around every agent session

Every agent invocation goes through the tool, which enforces policy at run time. At its best, it sees every session and
does not rely on each harness's permission model. It loses because harnesses already enforce permissions from their
files. The proxy also makes the tool a runtime dependency of every session, contrary to
[0010](0010-dependence-on-the-binary.md), moves policy from reviewable files into process logic, and has no effect when
a person starts the harness directly.

### The tool as a client of agent tool servers

Declared actions call tools on any tool server a team uses, such as a chat or ticketing server, without an agent run.
At its best, a team reuses servers it already configured, and integrations need no HTTP details. It loses because the
tool would hold tool-server credentials beside the harness's own, carry a protocol client, and decide which tool to
call with which arguments, which the protocol leaves to a model.

## Consequences

Costs:

- Enforcement is as strong as each harness's permission and hook model; the tool cannot stop an action the harness does
  not expose.
- A change to a harness's configuration format breaks the tool's output for that harness until the tool is updated
  ([0009](0009-agent-support.md)).
- Integrations that exist only as tool-server tools need an agent run.

Must be maintained:

- Knowledge of each supported harness's configuration surface across its versions ([0009](0009-agent-support.md)).

## Confirmation

- Review rejects any dependency on a model-provider SDK or a tool-server client library, and any code path that sends a
  prompt to a model.
- The test suite runs without model credentials and without network access to model providers.
- Fixtures that start a harness use a fake harness and assert the arguments and environment the tool passes to it.
