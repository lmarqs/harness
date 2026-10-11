# 0001. The tool is a software development lifecycle, never a worker

## Objective

**Problem.** Teams want every change to pass the same steps under the same guards, whether a person, a coding agent or
another program does the work, and the usual way to get it is copying a workflow by hand from repository to repository,
where the copies drift into dialects that lose safety settings. A tool that fixes this has to know what it is. Built
around one kind of program, it is rebuilt for the next one. Built to do the work itself, it grows into a second coding
agent, chasing every model and API change the vendors of coding agents already follow and holding credentials and costs
that belong to each organization.

**Goal.** Every change in every repository passes the same stages under the same guards, whatever program does its
work, and the programs that do the work keep the capabilities, model access and credentials their vendors and
organizations give them.

## Context

- A change passes the same points in every repository: it is planned, started, implemented, verified, completed,
  delivered, reviewed and merged, and it lands on the default branch ([0015](0015-development-lifecycle.md)).
- The work between those points is done by people and by programs that change a repository for someone: coding agents
  used interactively or started headless for one task, bots that open pull requests, codemods that rewrite code across
  a repository. A coding agent's harness is the program that runs a model in a loop with tools, and a loop runner is a
  separate program that starts a harness once per task from a list ([0022](0022-tool-integration.md)). Each harness
  reads its permissions, hooks, instructions and skills from files in the repository.
- What makes a change ready to leave the clone does not depend on who made it: whether it started from a recorded
  base, whether its checks pass against that base, whether it is committed and off the default branch
  ([0011](0011-safety-invariants.md)).
- Example: acme copies one coding-agent workflow by hand from `acme/api` into `acme/web` and `acme/infra`. Each copy
  is its own dialect. `acme/web` added a lint step and skips permission prompts through its unattended runner's
  configuration, `acme/infra` starts the second coding agent it uses for unattended work with a permission-bypass flag,
  and `acme/api` refuses both. All three run the same coding agents and models: the copies differ in how they guard a
  change and how they start the programs that do it, not in what those programs can do.
- Coding agents are products their vendors develop and change often. A program that reimplements an agent loop has to
  follow every model and API change the vendors already follow.
- Calling a model requires credentials, a provider choice, a cost budget and data-handling rules. Each of these is an
  organization's decision.
- Agent tool servers (the Model Context Protocol, MCP) each carry their own connection and credentials. Coding agents
  already hold these connections, decide when a model uses them and put each use under their permission model.
- Coding agents enforce permissions from their configuration files, and a person can always start one directly.
- Configuration a program reads from committed files is reviewable in a pull request. Behaviour inside a long-running
  process is not.

## Decision

1. The tool's role is the software development lifecycle of a repository's changes, whose stages, events, guards and
   proof [0015](0015-development-lifecycle.md) decides. Everything else it does, rendering, checking and removing the
   setup that the programs in those stages read, serves that lifecycle.
2. The work inside a stage belongs to people and to workers: programs that change the repository for someone, such as a
   coding agent, a bot or a codemod. The tool's code names no worker and no other program; every program it writes for
   or starts is one an ingredient describes ([0022](0022-tool-integration.md)).
3. The tool never calls a model API, holds no model credentials, runs no loop of a model with tools and makes no
   decision that needs a model.
4. The tool never acts as a client of the tool servers that coding agents connect to. Work that needs such a tool is
   done by a worker that owns the connection and its credentials ([0019](0019-action-kinds.md)).
5. When an attempt or a declared action needs a worker, the tool starts it as a child process, as its ingredient
   describes ([0009](0009-agent-support.md)), with arguments and environment the tool controls, and judges the outcome
   only by deterministic checks ([0011](0011-safety-invariants.md)).

## Reasons

- **1.** The hand-made copies in Context differ in how they guard a change and how they start the programs that do it,
  so the steps and their guards are the part that has to be one thing in every repository. What makes a change ready
  does not depend on who made it (Context), so a role defined by the lifecycle holds the same stages, guards and proof
  for a person, a coding agent and a bot alike. The setup the tool renders is committed, so reviewers read it as a diff
  and it keeps working when the tool is absent ([0010](0010-dependence-on-the-binary.md)). The trade: inside a stage,
  the tool can enforce only what a worker's own configuration exposes.
- **2.** The programs that do the work differ between teams and change on their vendors' schedules (Context). A tool
  whose concepts were one kind of worker would read wrong for every other kind and be rebuilt for each. Learning each
  program from an ingredient, not from the tool's own code, keeps the tool's job the same for every worker and for
  every program around them ([0022](0022-tool-integration.md)). The trade: the tool knows of a worker only what its
  ingredient says.
- **3.** A coding agent already follows model and API changes. A second model loop duplicates that work and competes
  with the coding agent a team chose. Credentials, provider choice and cost belong to organizations. A tool that holds
  none of them has no token cost, sends no data to a model provider and needs no data-handling review. The trade: every
  capability that needs reasoning comes from a worker, never from the tool.
- **4.** A tool-server connection carries credentials and grants actions that a model chooses at run time. A client in
  the tool would duplicate credentials the coding agent already holds, embed a protocol client in the binary, and make
  the tool choose which remote tool to call, which is a model's decision (point 3). Integrations that need no judgement
  reach a service through its HTTP API or command-line client instead ([0019](0019-action-kinds.md)). The trade: work
  available only as a tool-server tool needs an attempt, with its cost and its variance.
- **5.** Unattended runs are where a hand-copied setup loses its safety settings: a permission prompt stalls a run that
  nobody watches, so a copy turns the prompts off in the start-up arguments or the runner's configuration, as two of the
  three acme copies in Context do. The tool therefore controls the arguments, permission mode and environment of every
  worker it starts, including each one a loop runner asks it to start; the loop runner's own configuration stays the
  team's ([0022](0022-tool-integration.md)). It judges the outcome by checks it can replay, because a worker can claim
  completion that did not happen ([0011](0011-safety-invariants.md)). Starting the worker as an unmodified child
  process keeps it replaceable and keeps its reasoning inside it.

## Rejected alternatives

### A worker: the tool does the work, calling models in its own loop

At its best, one program controls prompts, tools, permissions and verification end to end, with no dependency on
third-party configuration formats. It loses because it competes with the coding agents teams already use, takes on
provider credentials and cost, must track model and API changes that the vendors already track, and removes the option
of keeping a preferred coding agent.

### A configuration layer for coding agents

The tool's concepts are coding agents and their sessions: it configures and constrains the programs that run coding
agents, and the lifecycle is one feature among others. At its best, its vocabulary matches what most teams run, and
every page speaks of programs its readers know. It loses because the stages, guards and proof of a change are the same
whatever program made it (Context), and a tool whose concepts are one kind of worker has none for a bot or a codemod
that opens changes: each would need a second set of concepts, or would change the repository outside the guards.

### A runtime proxy around every worker session

Every worker invocation goes through the tool, which enforces policy at run time. At its best, it sees every session and
does not rely on each worker's permission model. It loses because coding agents already enforce permissions from their
files. The proxy also makes the tool a runtime dependency of every session, contrary to
[0010](0010-dependence-on-the-binary.md), moves policy from reviewable files into process logic, and has no effect when
a person starts the worker directly.

### The tool as a client of tool servers

Declared actions call tools on any tool server a team uses, such as a chat or ticketing server, without an attempt. At
its best, a team reuses servers it already configured, and integrations need no HTTP details. It loses because the tool
would hold tool-server credentials beside the coding agent's own, carry a protocol client, and decide which tool to
call with which arguments, which the protocol leaves to a model.

## Consequences

Costs:

- Inside a stage, enforcement is as strong as each worker's permission and hook model; the tool cannot stop an action
  the worker does not expose.
- A change to a worker's configuration format breaks the tool's output for that worker until its ingredient is updated
  ([0009](0009-agent-support.md)).
- Integrations that exist only as tool-server tools need an attempt.
- The stages that need judgement, planning and review, get nothing from the tool beyond the events around them.

Must be maintained:

- An ingredient for each worker a repository uses, in the cookbook that publishes it, describing that worker's
  configuration surface across its versions ([0009](0009-agent-support.md)).
- The lifecycle as the one description of what the tool is, in every page that says it.

## Confirmation

- Review rejects any dependency on a model-provider SDK or a tool-server client library, and any code path that sends a
  prompt to a model.
- The test suite runs without model credentials and without network access to model providers.
- Fixtures that start a worker use a fake worker and assert the arguments and environment the tool passes to it.
- A fixture runs every stage of the lifecycle with a stand-in worker whose ingredient fills only its launch, a script
  that rewrites a file, and every guard and the replay apply to it as they do to a stand-in coding agent.
