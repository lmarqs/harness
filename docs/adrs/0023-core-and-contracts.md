# 0023. The binary is a core behind contracts it declares

## Objective

**Problem.** A tool that coordinates other programs mixes two kinds of logic: the rules it exists for, such as what it
owns, when an event fires and when completion is withheld, and the details of each program it talks to. When the two
are written together, a change in one program reaches into the rules, the rules cannot be tested without every
program installed, and nobody can say which lines are the promise and which are the plumbing.

**Goal.** The rules are written once, depend on no program, and are tested with no program installed; every program is
reached through an interface the rules declare.

## Context

- The binary's rules are the development lifecycle's ([0015](0015-development-lifecycle.md)): ownership, rendering,
  events, guards, attempts, proof and removal ([0007](0007-ownership.md), [0011](0011-safety-invariants.md)). None of
  them is about any program in particular.
- What the rules need from outside is small and regular. From version control: the base revision, a file at a revision,
  whether the tree is clean, whether a path is tracked, what changed since a revision, the common ancestor of two
  revisions, whether a branch's changes already reached another, the name of the shared default line of work and the
  revision it is at, a reset, a commit, a branch, learning what is shared, a workspace, and where the checkout's version
  control metadata lives. From a worker: how to launch it and, where it has them, the files it owns and their content,
  the arguments that start it unattended, the arguments that would bypass its checks, the arguments that let it use the
  network, and the fields of its hook payload. From a jail that confines a worker's attempts: how to start it around a
  command, share a directory, pass a variable and open the network ([0027](0027-worker-jails.md)). From any other
  program: run this and tell me the exit status.
- A program's answers arrive as a string, lines, bytes or an exit status (Context of
  [0022](0022-tool-integration.md)). Writing in a program's format and reading structured output can be described as
  data; parsing a program's free text in general cannot.
- Ports-and-adapters is the established name for this shape: the core declares the interfaces it needs, the
  connections implement them, and the entry point wires the two. Its known payoffs are technology independence,
  testing with stand-ins and reuse of the core by several entry points.
- Every ingredient and every station is data the binary interprets, never code it loads
  ([0022](0022-tool-integration.md), [0024](0024-ingredients-and-stations.md), [0006](0006-repository-contract.md)).
- The binary ships no ingredient, and a repository has the ingredients its data pins and nothing else
  ([0022](0022-tool-integration.md)). A newly adopted repository can have pinned none.
- Version control systems differ in how much they share and where. Some keep a full copy of the history in every clone
  and learn what others shared only when asked, so the revision of the shared default line of work in a clone is the one
  it last learned, which may differ from that of its own default branch; others keep the history on a server, and some
  share nothing beyond the one copy. Example: git answers the shared default line as `origin/main`, learns it with a
  fetch, and a commit on the local `main` is shared only once it is pushed.
- The repository's declared data sits in the tool's configuration directory at the repository root
  ([0014](0014-directory-layout.md)), so finding it takes only the files of a checkout. The base revision, a file's
  history and a workspace are answers only version control has.

## Decision

1. The binary has three kinds of code. The core: the rules, the data models and the interfaces the rules need from
   outside. The connections: the implementations of those interfaces. The entry point: the command line, which reads
   the repository's declared data, resolves the ingredients, wires the connections and runs one use case.
2. The core depends on nothing outside itself. The connections depend on the core; the entry point depends on both.
   No name of a program, a file format of a program, or an argument of a program appears in the core, and the core
   names what it needs by its role, never by one program's term for it: a separate working copy for a task is a
   workspace, whatever the version control calls it.
3. The core declares three contracts that ingredients fill with data. The version control contract is a fixed set of
   operations, each with a declared output kind. It asks as little of a version control system as the rules allow: it
   names the shared default line of work and the revision it is at, and learning what is shared, never one system's
   mechanism for them; the operation that gives the revision and the one that learns what is shared are optional, and
   for each the core declares what it does without it: it takes the line's name as the revision, or goes on with what
   the checkout knows after one note. The operation that lists where the checkout's version control metadata lives is
   optional too, and only a worker that runs in a jail needs it ([0027](0027-worker-jails.md)). The worker contract is
   the launch of one worker, its one required part, and, where the worker has them, its unattended arguments, its bypass
   arguments, its network arguments, its renders, its hook payload mapping, its instructions location, its skill
   location and its native rule import ([0009](0009-agent-support.md)). The jail contract, optional and used only around
   the attempts of a worker whose declaration names a jail, is the jail's launch and its arguments that share a
   directory, read-only or with write access, pass a variable or keep the environment, and, where the jail has them,
   share its working directory, end its own arguments, open the network and would turn the confinement off
   ([0027](0027-worker-jails.md)). One connection interprets an ingredient's operations for each contract.
4. The core declares, besides those, only generic interfaces: the files of a checkout, running a command and reading
   its exit status and output, an HTTP request, the release source, the clock, and presenting a plan, a diff, a
   finding or a question to a person. Each has one connection and an in-memory stand-in for tests.
5. The entry point resolves every ingredient the use case needs, and the station a use case applies, from the verified
   cache before the use case runs, and hands the use case the connections configured with them. Repository data
   chooses parameters and names ingredients; it never supplies a connection.
6. The entry point is the only way in. People, hooks fired by a worker and loop runners all call the command line
   ([0022](0022-tool-integration.md)); the committed wrapper is such a caller ([0017](0017-engine-version.md)).
7. The core finds the repository root itself, through the files interface: the nearest directory upward that holds the
   tool's configuration directory with its data ([0014](0014-directory-layout.md)). Reading and validating the
   repository's data needs no ingredient; workspace, base and history operations go through the version control
   contract.
8. A contract that no ingredient in use fills makes every use case that needs it unavailable. An explicit call of such a
   use case stops before it acts and reports a missing prerequisite that names the contract and the step that fills it;
   an implicit call follows [0010](0010-dependence-on-the-binary.md). The binary never substitutes a fallback for an
   unfilled contract. The health check reports each unfilled contract with the use cases it makes unavailable.

## Reasons

- **1, 2.** Context shows the rules are about no program and need little from any. Keeping program names and formats out
  of the core is what makes the rules the same for every program and lets a program change without a change to a rule. A
  program's term in the core would also leak into commands, settings and pages, which would then read wrong for every
  other program that fills the same contract. The trade: every outside need passes through an interface, which is one
  more indirection to read.
- **3.** The things the rules rely on answers from, version control, the worker and, around a worker's attempts, a jail,
  get named contracts so that an ingredient can fill them with data (Context) and a test can fill them with a stand-in.
  A fixed operation set with declared output kinds is what keeps a description possible: the binary never has to parse
  free text. The version control contract speaks of what is shared and of the shared default line of work, because
  systems differ in what a clone holds and how it learns more (Context); a system that keeps no separate shared copy has
  no other revision to give and nothing to learn, so those two operations are optional with a defined behaviour in their
  absence, which is part of the contract, not a description of any program. The metadata operation is optional for the
  same reason a jail is: a repository that confines no worker needs nothing new from its version control system. The
  worker contract requires only the launch because a worker can have no configuration of its own
  ([0009](0009-agent-support.md)). The jail contract is the third because only the binary can place the worker's launch
  inside the jail's and keep both programs' bypass arguments out of it, and it is optional because most repositories
  confine no worker ([0027](0027-worker-jails.md)).
- **4.** The remaining needs are not about any program. One generic connection for each keeps the list of connections
  short, and the in-memory stand-ins are what let every contract test for an invariant run without a disk, a
  network, a version control program, a worker or a jail on the machine.
- **5.** Resolving before the use case runs is what [0022](0022-tool-integration.md) requires of attempts, and doing
  it in the entry point keeps resolution, caching and verification out of the rules. Data that could supply a
  connection would let the repository, and a worker running in it, choose how the repository is read
  ([0011](0011-safety-invariants.md)).
- **6.** One entry means one place where arguments are checked, ingredients are resolved and exit statuses are mapped,
  and it is what lets any program drive the binary without code on either side.
- **7.** A repository can have pinned no ingredient (Context), and it is exactly then that a person needs the binary
  to read its data and say what is missing. If finding the root went through version control, every use case,
  validation and the health check included, would need the version control ingredient before it could report that the
  ingredient is absent. The
  data's location is a fact of the tool's own layout, not of any program (Context), so the core finds it with the files
  interface alone, and only the answers version control alone has go through its contract. The trade: the root is where
  the tool's configuration directory is, which a person can place below the top of a version control checkout, and the
  binary then treats that directory as the repository.
- **8.** A fallback for an unfilled contract would be a description of a program inside the binary, which
  [0022](0022-tool-integration.md) excludes, acting on the repository without any pin a reviewer accepted, and wrong in
  every repository that uses another program. Running the use case without the contract, or reporting success, would
  report as done work that did not happen ([0010](0010-dependence-on-the-binary.md)). Stopping with the contract's name
  turns the gap into one step a person can take, and the health check shows the whole gap at once. The trade: a
  repository that has pinned no ingredient can do little beyond reading, validating and reporting until it pins one.

## Rejected alternatives

### Rules and program details written together

Each command of the binary calls the version control program and the worker directly where it needs them. At its best,
the code is shortest and every call is visible where it happens. It loses because a program's change reaches into the
rules, the rules cannot be tested without the programs installed, and the binary's source names programs, which
[0022](0022-tool-integration.md) forbids.

### A connection per program, compiled in

The core declares the contracts, and the binary ships one hand-written connection for the version control system and one
for each common coding agent. At its best, each connection is ordinary code with no description format to interpret. It
loses to [0022](0022-tool-integration.md): a program's description then lives in code only the project can change, and
the mechanism differs from every other program's.

### Loadable connections

Connections are loaded at run time from outside the binary. At its best, anyone adds a program without a release. It
loses because loadable code is engine code outside the binary ([0006](0006-repository-contract.md)), needs a runtime
or a loading protocol ([0004](0004-runtime-dependencies.md)), and a data ingredient already lets anyone add a program.

### A fallback when no ingredient fills a contract

When a repository pins no version control ingredient, the binary assumes the commands of the version control system
nearly every repository uses, and when a worker has no ingredient, it renders nothing for it and starts it with no
arguments. At its best, a repository works the moment it is adopted, before anyone picks a cookbook. It loses because
the assumed commands are an ingredient shipped under another name ([0022](0022-tool-integration.md)), unpinned and
unreviewed, they fail in a different way in every repository that uses another program, and a worker started without
its ingredient runs without that ingredient's floor ([0011](0011-safety-invariants.md)).

### The repository root found through version control

The version control contract answers where the repository's top is, and the core reads the data from there. At its best,
the root is always the one version control reports, and the core has one fewer rule of its own. It loses because
reading the data would then need the version control ingredient, so a repository without one could not be told that it
lacks it, and the data's location is the tool's own layout, which needs no program to find.

## Consequences

Costs:

- Every outside need is an interface plus a connection plus a stand-in, which is more code than a direct call.
- A need the contracts do not cover, such as an operation a new version control system lacks, is a change to the
  contract and therefore to the ingredient schema.
- Until a repository pins an ingredient for a contract, every use case that needs that contract is unavailable in it.
- A configuration directory placed below the top of a version control checkout makes that directory the repository's
  root for the tool.

Must be maintained:

- The dependency direction, checked in CI.
- The three contracts and the generic interfaces as the complete list of what the core needs from outside.
- An in-memory stand-in for every interface, kept equivalent to its connection by a shared test suite.

## Confirmation

- A CI check fails when a core package imports a connection or the entry point, or when the core's source contains the
  name of an external program.
- Every contract test for an invariant in [0011](0011-safety-invariants.md) runs with the in-memory stand-ins only,
  in a job with no version control program and no worker installed.
- Each connection and its stand-in pass the same behavioural suite.
- In a fixture repository that pins no ingredient, with no version control program installed, validation and the health
  check run from any directory below the root, and the health check names each unfilled contract with the use cases it
  makes unavailable.
- In the same fixture, every use case that needs a contract stops as a missing prerequisite that names the contract,
  starts no program and writes nothing.
- A stand-in version control ingredient without the optional operations runs every use case that confines no worker:
  where the core needs the revision of the shared default line of work it uses the line's name, and where it would
  learn what is shared it reports one note and goes on.
- A stand-in jail ingredient around a stand-in worker receives the launch the core composes, in the order
  [0027](0027-worker-jails.md) fixes, and the core's source names no jail program.
