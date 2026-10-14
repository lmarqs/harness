# 0027. A worker's attempts run inside the jail its declaration names

## Objective

**Problem.** A worker in an attempt runs with the person's account: it can read the person's home, with its keys and
tokens, write wherever the account can, and reach the network, and so can every command it writes that the replay of
its proof later runs. The attempt contract bounds the worker through its own permission checks and the binary's guards,
but a worker program's permission checks are only as strong as that program, and the code the worker writes runs
outside them. Programs that confine another program exist, each with its own arguments, and a team that wraps a worker
in one by hand, around the command its loop runner runs, confines the binary along with the worker.

**Goal.** A repository can have every attempt of a worker, and every command that runs what that worker wrote, run
confined to the directories, variables and network the attempt needs, through a jail program the repository chooses,
while the binary's own work and its guards stay outside the confinement and out of the worker's reach.

## Context

- Programs exist that start another program confined on the same host, running the host's own programs at their host
  paths. ai-jail, bubblewrap and firejail are such programs, and each takes arguments of its own.
- ai-jail 2.8.1 on Linux, which runs through bubblewrap, passes the confined command only a small allowlist of
  variables and those it is told to forward by name, each with the host's value; any other variable is absent. It
  shares the directory it is started in read-write, gives the command a fresh home directory and a fresh `/tmp`, both
  discarded at exit, and shares further directories read-only or read-write by name. Unless its network option is
  given, the command reaches no network: neither name resolution nor direct connections work, and a service on the
  host's own loopback address is unreachable too. A preset for one worker program adds that program's API host and the
  package registries to a filtered proxy. ai-jail writes a configuration file of its own into the project directory
  unless an option tells it not to.
- In ai-jail 2.8.1, a read-only share of a path inside the read-write project directory stays read-only. A read-write
  share that overlaps a read-only one is ignored: ai-jail prints "ignoring rw-map that overlaps a read-only map
  destination" and launches anyway, without that share.
- ai-jail 2.8.1 passes the standard input, the standard output and the exit status through only in its
  direct-execution mode, which runs the command without its pseudo-terminal proxy. Whether a jail passes the streams
  and the exit status through is a property of the program and its mode that its arguments do not show.
- bubblewrap 0.9.0 shares no directory it is not given. It binds host directories read-only or read-write into a new
  mount namespace, in the order given, sets or clears variables, opens a new network namespace on request, starts the
  command in a directory it is given, and ends its own options with a separator; starting a command in its working
  directory read-write takes a read-write bind of that directory onto itself and a change into it.
- ai-jail 2.8.1 shares the metadata of a linked git working copy only when a separate option asks for it. In a linked
  git working copy, the metadata is spread over the working copy's own git directory, the repository's common directory
  and a `.git` file at the top of the working copy that points to the first; git 2.43 reports the first two through
  `git rev-parse`.
- The version control program runs code that its metadata names on the binary's own operations. git 2.43 runs the
  hooks in the repository's hooks directory, or in the directory `core.hooksPath` names, which can be a tracked
  directory, on a commit, and its configuration names programs that other commands start, such as a file system
  monitor on `git status`.
- The binary recognises a worker it started unattended by a mark in that worker's environment, and the guards that keep
  lifecycle events out of the worker's hands rest on it. A second mark tells the
  session-start hook that the prompt already carried the standing context ([0020](0020-standing-context.md)). A jail
  that passes only the variables it is given drops every mark it is not given.
- Each working copy keeps its own state directory inside itself: the proof of verification, the session, the hook
  cache, the run record and an attempt's output. The primary working copy's state directory also holds the lifecycle
  log and the lock for creating working copies, which the binary writes outside any worker, and by default it holds
  every managed working copy as well ([0014](0014-directory-layout.md)).
- The tamper check, the replay and the commit of an attempt read and write the attempt's working copy and its version
  control metadata from outside the worker.
- The replay of a proof runs the repository's lane commands and validators on the content the worker left, so it runs
  code the worker wrote, such as a test the worker added or a script a lane calls.
  The later steps of a recipe run run on the same content ([0025](0025-recipes.md)).
- The binary resolves every ingredient an attempt uses from the per-user cache before the worker starts, and nothing
  downloads during an attempt ([0022](0022-tool-integration.md)). The tool's commands that a worker runs, such as a
  verification and the hooks, load the pinned release and the ingredients from that cache.
- A worker program reads its login and its settings from the person's home, in places that differ per program. A jail
  with a fresh home sees none of them unless something shares them.
- A container image brings its own copies of the programs it runs, built for the image's platform; docker starts a
  container from an image and binds host directories into it with arguments of its own. A program started over ssh runs
  on the other machine's filesystem, and OpenSSH appends the arguments of a remote command to it, separated by spaces,
  and sends that one line to the server to run.
- A recipe run or a loop run can itself start inside a container or on another machine, with the tool installed there,
  through whatever starts it, such as the command a loop runner runs for each task ([0025](0025-recipes.md)).

## Decision

1. A worker declaration in the repository's declared data may name a jail around the worker's attempts: a pinned
   ingredient that fills the jail contract, and the jail's options the repository chooses, a list of arguments, empty
   unless given. The binary starts the worker of every attempt of that worker through the jail, whether the attempt
   command runs it on its own, for a lifecycle action or as a recipe step. The binary starts no interactive session, so
   no jail wraps one: a person who wants a session confined starts it inside the jail, and the rendered files apply
   there as they do without one.
2. The core declares a third contract, the jail contract, beside the version control and worker contracts
   ([0023](0023-core-and-contracts.md)). A jail ingredient gives the jail's launch, the arguments that share one
   directory with write access, the arguments that share one directory read-only, and either the arguments that pass
   one variable or the statement that the jail keeps the environment it is started with, exactly one of the two. Where
   the jail has them, it also gives the arguments that share the jail's working directory read-write and start the
   confined command in it, without which the jail shares the directory it is started in read-write by itself; the
   arguments that end the jail's own and begin the confined command; the arguments that open the network to it; and
   the arguments that would turn the confinement off, marked as floor. What no argument can show, the ingredient's
   documentation states for its program: the jail passes the standard streams through unchanged, exits with the
   confined command's exit status, and gives a read share inside its working directory precedence over the working
   directory.
3. The binary composes the jailed launch itself, in a fixed order:
   1. the jail's launch;
   2. the working-directory share, with the attempt's working copy;
   3. the write shares;
   4. the read shares, the version control metadata first and the per-user cache after it;
   5. one variable after another;
   6. the jail's network arguments, when the worker's attempts may use the network;
   7. the repository's options for the jail;
   8. the separator;
   9. the worker's launch exactly as it is without a jail, its own network arguments included.

   It starts the jail with the attempt's working copy as its working directory, and never passes the working copy as a
   write share. It refuses the floor bypass arguments of both ingredients wherever they appear in the whole launch: a
   bypass argument of the jail among the repository's options fails validation, and any other is refused at launch,
   named.
4. The working copy is the jail's working directory, shared read-write. The write shares are the directories the worker
   declaration lists as writable, and nothing else: the clone's state directory is in no worker's writable set. The
   read shares are the paths of the working copy's version control metadata and the per-user cache. The binary never
   passes a write share that contains a read share. It shares nothing else of the person's home: how the worker
   program's own login and settings reach the jail is the jail's own configuration or the repository's options. It
   passes every mark it sets for the worker, the switch that turns standing context off when the attempt itself runs
   with it, and the location of the per-user cache. Inside the jail the cache is read-only: the tool's commands there
   write nothing to it and download nothing.
5. From the moment the worker starts until the attempt ends, every command the binary starts on the attempt's working
   copy runs in the worker's jail, with the same shares, variables, network and options, that command taking the
   worker's place: the replay's lane commands and validators. In a recipe run, once an attempt step of a jailed worker
   has run, the later command steps, the run's verification and the replay at completion run in that jail too. What runs
   before the worker starts, such as the bootstrap on the base, and the binary's own work that runs no code of the
   working copy, such as the version control operations, the tamper check, the commit and lifecycle actions, runs
   outside the jail with the person's environment. A version control operation that would run code the working copy
   defines, such as hooks kept in a tracked directory, turns that code off where the version control program allows.
6. The version control contract has an optional operation that lists, as absolute paths, where the working copy's
   version control metadata lives: its history, its configuration and the hooks the program runs. The binary uses it
   only to share those paths read-only inside a jail. A worker declared with a jail, in a repository whose version
   control ingredient lacks the operation, fails validation.
7. Validation refuses, before anything runs: a jail ingredient that is not pinned or does not fill the jail contract;
   one that states neither or both ways of passing variables, or lacks either kind of share; network access for a
   worker whose jail cannot open the network; a jail bypass argument among the repository's options for the jail; a
   writable directory of a jailed worker that contains a version control metadata path or the per-user cache; and a
   jailed worker whose version control ingredient lacks the metadata operation. A jail program that is not installed,
   and a jail ingredient that is not in the per-user cache, are missing prerequisites that stop the attempt before the
   worker starts and name what is missing. The health check checks the jail's version against its ingredient's range
   and reports a jail program that is not installed, as it does for a worker.

## Reasons

- **1.** Which program confines a worker, and with which options, differs between repositories and machines, and so
  does which worker needs it, so the choice is repository data on the worker declaration, like the worker's role and
  network access. Attempts are where nobody watches,
  and they are what the binary starts; it starts no interactive session, so it has none to wrap, and a person in a
  session can see what the worker does. The trade: an interactive session is confined only when a person starts it so.
- **2.** A jail's arguments are facts about its program, and they differ between programs (Context), so an ingredient
  holds them, pinned and reviewed like any program's facts ([0022](0022-tool-integration.md),
  [0026](0026-cookbook-trust.md)), and the core holds only what they mean and their order. Both kinds of share and one
  way to pass variables are required because without them the binary cannot open the writable directories, keep the
  metadata out of the worker's reach or pass the marks (point 4). The working-directory share is optional because some
  jails share the directory they start in by themselves and others take arguments for it (Context); the separator and
  the network are optional because some jails need neither. The floor marks the arguments that would undo the
  confinement, as a worker's floor marks those that undo its permission checks.
  Passing the streams through, the exit status and the precedence of a read share inside the working directory do not
  show in arguments and depend on a program's mode (Context), so documentation states them and the cookbook that
  publishes the ingredient tests them. The contract asks for precedence only there because that is all the binary
  relies on: the metadata lives inside the working copy (point 4).
- **3.** Only a launch the binary composes lets it tell the jail's arguments from the worker's, so the refusal of
  bypass arguments covers both programs, and the worker's launch, pinned by its own ingredient, keeps its meaning inside
  the jail. One fixed order makes every jail's launch read the same, with the read shares after the shares they sit
  inside. The working copy goes in as the working directory and never as a write share, because a write share over a
  read share may be dropped by the jail with only a message (Context). The network opens only when the worker's own
  arguments and the jail's both open it, because each program closes it in its own way.
  A jail bypass argument in the repository's data is caught when the data is validated, before any attempt; one among a
  caller's arguments can only be caught at launch.
- **4.** The worker writes its working copy, and the writable directories the declaration lists serve the repository's
  own tools, such as a tracker's data. The clone's state directory is in no writable set because each working copy
  keeps its own state inside itself, the primary working copy's state is written by the binary outside any worker, and
  that directory holds every managed working copy by default, so opening it would let a worker in one working copy write
  every other (Context). A repository whose attempts need another directory names it. The metadata is read-only because
  the binary's own operations, outside the jail, run the configuration and hooks it holds (Context, point 6). A write
  share that contains a read share is refused because a jail may drop the write share with only a message and launch
  anyway (Context), and the worker would then lack a directory the repository declared, or, in a jail with the opposite
  precedence, could write the metadata. The cache holds what the tool's commands inside the jail load (Context);
  read-only, it cannot be changed under a later attempt. Sharing nothing else of the home keeps the person's keys and
  tokens out, which is what a jail is for. Passing the marks keeps the lifecycle guards and
  the delivery of [0020](0020-standing-context.md) working inside a jail that drops what it is not given (Context), and
  passing the cache's location lets the tool inside find it whatever home the jail gives. Nothing inside needs to
  download, because the attempt resolved everything before the worker started (Context). The trade: the worker
  program's login and settings reach the jail only as the jail is configured to share them.
- **5.** The replay runs code the worker wrote (Context). Outside the jail, that code would run with the person's whole
  reach, which is exactly what the jail withholds from the worker; in the same jail it runs with the worker's reach and
  no more. A recipe run's later steps run on the worker's edits too. The bootstrap runs on the base, before the worker
  wrote anything. The binary's own work needs what the jail withholds, the metadata writable, the person's credentials
  for delivery and the network, and runs no code the worker wrote, except where the version control program runs code
  its configuration names (Context), which the version control operation turns off where the program allows. The trade:
  lanes run in the jail's environment, which may lack a variable or a service a lane needs, such as a database on the
  host's loopback address (Context), and a lifecycle action that runs a command the working copy defines runs code the
  worker wrote, outside the jail.
- **6.** Where the metadata lives is a fact of the version control system and of the working copy's layout, and it is
  not one place in every layout (Context), so the version control ingredient answers it, as the contract answers every
  question about history ([0023](0023-core-and-contracts.md)). It is optional because only a jail needs it: a
  repository with no jailed worker needs nothing new from its version control ingredient. A jailed worker without it
  fails validation because the metadata would then stay writable inside the jail, and the binary's own commit, outside
  the jail, would run the hooks and configuration the worker wrote (Context): the binary would undo the confinement
  itself.
- **7.** Each refused state is one in which the jail would confine less than the declaration says, or the binary could
  not keep the guarantees of points 3 to 6. Validating the repository's data before anything runs, as for every
  declaration, stops it before any worker starts ([0023](0023-core-and-contracts.md)). A missing program or an uncached
  ingredient is a step a person takes, reported as for a worker ([0022](0022-tool-integration.md)).

## Rejected alternatives

### The whole loop run in a jail

The baseline: a repository wraps the command its loop runner runs for each task, or the loop runner itself, in a jail,
and the binary changes nothing. At its best, the tool needs no change, every jail works at once, and everything inside,
the binary included, is confined. It loses because the binary and the worker then share one jail. The version control
metadata the binary's commit and tamper check use, and the base the rules are read from, must be writable for the binary
and so are writable for the worker, and delivery, which lifecycle actions run, needs credentials and the network inside
the jail, which opens them to the worker. The confinement then binds the binary's own work instead of setting the worker
apart from it.

### A free-form command prefix on the worker declaration

The worker declaration holds a list of arguments that the binary puts before the worker's launch. At its best, any
jail works with no contract and no ingredient, and the repository writes exactly the command a person would type. It
loses because the binary cannot place the worker's launch where the jail expects it, cannot tell the jail's arguments
from the worker's when it refuses bypass arguments, and cannot add the shares and pass the marks, and because every
repository retypes the jail's facts instead of pinning them, where they drift as hand-kept configuration does
([0022](0022-tool-integration.md)).

### Container images as jails

The worker runs in a container started from an image, with the working copy bound in (Context). At its best, the
environment is the same on every machine, and the isolation is strong. It loses because the image brings its own
copies of the programs: the version checks, the floor and the renders describe the host's program while another one
runs, and the binary inside would need a build for the image's platform. A repository that wants a container runs the
whole recipe run or loop run in one, with the tool installed there (Context), which needs nothing of the core.

### Remote hosts as jails

The worker runs on another machine, reached over a remote shell. At its best, the worker cannot touch the person's
machine at all. It loses because the remote shell joins the command's arguments into one line that the other machine
parses again (Context), so a value reaches a shell, which the binary never lets happen
([0019](0019-action-kinds.md)), and because the worker's changes are on another filesystem, where the tamper check, the
replay and the commit cannot see them. A repository runs the whole run on the other machine instead.

### The replay outside the jail

Only the worker runs in the jail; the replay and a recipe run's later steps run with the person's environment. At its
best, the binary's part is simpler, and the lanes see every variable and service the person has, so a lane behaves in
an attempt as it does when a person runs it. It loses because the replay runs code the worker wrote (Context), so that
code would run unconfined, which is what the jail exists to prevent.

## Consequences

Costs:

- A lane that needs a variable or a service the jail withholds fails in the attempts of a jailed worker, and passes
  outside them, until the repository's options for the jail pass it.
- A repository whose attempts need a directory beyond the working copy names it as writable; nothing else is opened.
- Each jail ingredient's documentation states its program's precedence of a read share inside the working directory,
  that it passes the streams and the exit status through, its default network and how a worker program's login reaches
  it; the core relies on what no argument shows.
- The tool's commands tolerate a read-only cache inside a jail: there they skip the update check and every other cache
  write.
- A jailed worker needs a version control ingredient that answers the metadata operation.
- Lifecycle actions run outside the jail, so an action that runs a command the working copy defines runs code a worker
  wrote, with the person's reach.
- The binary confines no interactive session.

Must be maintained:

- The jail contract, the order in which the binary composes the launch and the variables it passes, as part of the
  public contract.
- A stand-in jail in the project's fixtures, and in each cookbook that publishes a jail ingredient, tests of the three
  properties no argument shows.

## Confirmation

- A fixture jail that drops every variable it is not passed starts a fixture worker that still sees the environment
  mark, and the worker's attempt to trigger a lifecycle event is refused.
- A fixture worker in a jail cannot write the fixture's version control metadata, and the commit after its passing
  replay runs no hook the worker wrote.
- A fixture lane that writes outside the working copy and the writable directories during the replay of a jailed
  attempt fails.
- The launch composed for a fixture jail holds the jail's launch, the working-directory share with the working copy,
  the write shares, the read shares after them, the variables, the jail's network arguments only for a worker whose
  attempts may use the network, the repository's options, the separator and the worker's launch, in that order, and
  holds the working copy in no write share and the clone's state directory in no share.
- A fixture recipe run whose attempt step runs a jailed worker runs its later command steps, its verification and its
  replay in the same jail, and its bootstrap and its commit outside it.
- Each of these fails validation before any program starts: a jail ingredient that is not pinned, one that fills
  another contract, one that states neither or both ways of passing variables, one without a write share or a read
  share, network access for a worker whose jail cannot open it, a jail bypass argument among the repository's options,
  a writable directory of a jailed worker that contains the version control metadata or the per-user cache, and a
  jailed worker whose version control ingredient lacks the metadata operation.
- A jail bypass argument among a caller's arguments is refused at launch, named, and no program starts.
- A jail program that is not installed, and a jail ingredient that is not cached, each stop the attempt as a missing
  prerequisite before the worker starts, naming what is missing, and the health check reports the missing jail program.
