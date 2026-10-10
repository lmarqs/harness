# 0023. The binary is a core behind contracts it declares

## Objective

**Problem.** A tool that coordinates other programs mixes two kinds of logic: the rules it exists for, such as what it
owns, when an event fires and when completion is withheld, and the details of each program it talks to. When the two
are written together, a change in one program reaches into the rules, the rules cannot be tested without every
program installed, and nobody can say which lines are the promise and which are the plumbing.

**Goal.** The rules are written once, depend on no program, and are tested with no program installed; every program is
reached through an interface the rules declare.

## Context

- The binary's rules are about ownership, rendering, events, guards, attempts, proof and removal
  ([0007](0007-ownership.md), [0011](0011-safety-invariants.md), [0015](0015-development-lifecycle.md)). None of
  them is about any program in particular.
- What the rules need from outside is small and regular. From version control: the base revision, a file at a revision,
  whether the tree is clean, whether a path is tracked, what changed since a revision, the common ancestor of two
  revisions, whether a branch's changes already reached another, a reset, a commit, a branch, an update from the remote,
  a worktree. From a harness: the files it owns and their content, the arguments that start it unattended, the arguments
  that would bypass its checks, and the fields of its hook payload. From any other program: run this and tell me the
  exit status.
- A program's answers arrive as a string, lines, bytes or an exit status (Context of
  [0022](0022-tool-integration.md)). Writing in a program's format and reading structured output can be described as
  data; parsing a program's free text in general cannot.
- Ports-and-adapters is the established name for this shape: the core declares the interfaces it needs, the
  connections implement them, and the entry point wires the two. Its known payoffs are technology independence,
  testing with stand-ins and reuse of the core by several entry points.
- Every recipe is data the binary interprets, never code it loads ([0022](0022-tool-integration.md),
  [0006](0006-repository-contract.md)).

## Decision

1. The binary has three kinds of code. The core: the rules, the data models and the interfaces the rules need from
   outside. The connections: the implementations of those interfaces. The entry point: the command line, which reads
   the repository's declared data, resolves the recipes, wires the connections and runs one use case.
2. The core depends on nothing outside itself. The connections depend on the core; the entry point depends on both.
   No name of a program, a file format of a program, or an argument of a program appears in the core.
3. The core declares two contracts that recipes fill with data: the version control contract, a fixed set of
   operations, each with a declared output kind, and the harness contract, the renders, launch arguments, bypass
   arguments, hook payload mapping, instructions location, skill location and native rule import for one agent
   ([0009](0009-agent-support.md)). One connection interprets a recipe's operations for each contract.
4. The core declares, besides those, only generic interfaces: the files of a checkout, running a command and reading
   its exit status and output, an HTTP request, the release source, the clock, and presenting a plan, a diff, a
   finding or a question to a person. Each has one connection and an in-memory stand-in for tests.
5. The entry point resolves every recipe the use case needs from the release or the verified cache before the use
   case runs, and hands the use case the connections configured with them. Repository data chooses parameters and
   names recipes; it never supplies a connection.
6. The entry point is the only way in. People, hooks fired by a harness and loop runners all call the command line
   ([0022](0022-tool-integration.md)); the committed wrapper is such a caller ([0017](0017-engine-version.md)).

## Reasons

- **1, 2.** Context shows the rules are about no program and need little from any. Keeping program names and formats
  out of the core is what makes the rules the same for every program and lets a program change without a change to a
  rule. The trade: every outside need passes through an interface, which is one more indirection to read.
- **3.** The two things the rules rely on answers from, version control and the harness, get named contracts so that a
  recipe can fill them with data (Context) and a test can fill them with a stand-in. A fixed operation set with
  declared output kinds is what keeps a description possible: the binary never has to parse free text.
- **4.** The remaining needs are not about any program. One generic connection for each keeps the list of connections
  short, and the in-memory stand-ins are what let every contract test for an invariant run without a disk, a
  network, git or an agent on the machine.
- **5.** Resolving before the use case runs is what [0022](0022-tool-integration.md) requires of unattended attempts,
  and doing it in the entry point keeps resolution, caching and verification out of the rules. Data that could supply
  a connection would let the repository, and an agent working in it, choose how the repository is read
  ([0011](0011-safety-invariants.md)).
- **6.** One entry means one place where arguments are checked, recipes are resolved and exit statuses are mapped, and
  it is what lets any program drive the binary without code on either side.

## Rejected alternatives

### Rules and program details written together

Each command of the binary calls git and the harness directly where it needs them. At its best, the code is shortest
and every call is visible where it happens. It loses because a program's change reaches into the rules, the rules
cannot be tested without the programs installed, and the binary's source names programs, which
[0022](0022-tool-integration.md) forbids.

### A connection per program, compiled in

The core declares the contracts, and the binary ships one hand-written connection for git, one for each harness. At
its best, each connection is ordinary code with no description format to interpret. It loses to
[0022](0022-tool-integration.md): a program's description then lives in code only the project can change, and the
mechanism differs from every other program's.

### Loadable connections

Connections are loaded at run time from outside the binary. At its best, anyone adds a program without a release. It
loses because loadable code is engine code outside the binary ([0006](0006-repository-contract.md)), needs a runtime
or a recipe protocol ([0004](0004-runtime-dependencies.md)), and a data recipe already lets anyone add a program.

## Consequences

Costs:

- Every outside need is an interface plus a connection plus a stand-in, which is more code than a direct call.
- A need the contracts do not cover, such as an operation a new version control system lacks, is a change to the
  contract and therefore to the recipe schema.

Must be maintained:

- The dependency direction, checked in CI.
- The two contracts and the generic interfaces as the complete list of what the core needs from outside.
- An in-memory stand-in for every interface, kept equivalent to its connection by a shared test suite.

## Confirmation

- A CI check fails when a core package imports a connection or the entry point, or when the core's source contains the
  name of an external program.
- Every contract test for an invariant in [0011](0011-safety-invariants.md) runs with the in-memory stand-ins only,
  in a job with no git and no agent installed.
- Each connection and its stand-in pass the same behavioural suite.
