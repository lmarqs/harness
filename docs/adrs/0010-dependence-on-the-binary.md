# 0010. Nothing in an adopted repository depends on the binary

## Objective

**Problem.** Not everyone who clones a repository installs the tool, and CI pipelines should not have to. If anything in
an adopted repository needs the binary, adoption forces it on every teammate and pipeline, and a missing binary breaks
agent sessions or builds for people who never chose the tool.

**Goal.** A teammate or pipeline without the tool works exactly as before adoption, and a repository can adopt or
withdraw one practice at a time.

## Context

- Not every teammate, machine or pipeline installs the tool, yet everyone who clones a repository shares its committed
  files.
- Repositories commonly run hosted CI workflows, and their build, test, deploy and release outputs are what the team
  ships.
- Hooks run inside agent sessions on every matching event. A hook that fails interrupts every session of every
  teammate, and a hook invocation has a budget of 50 ms at p95 ([0003](0003-implementation-language.md)).
- The committed wrapper fetches the pinned release over the network on first use and keeps it in a per-user cache
  ([0017](0017-engine-version.md)). Until then, a machine has no binary to run.
- Entry points are called in two ways. Agents call hooks implicitly, on events, without anyone asking for a result.
  People and agents call task-runner shims (one-line tasks that call the wrapper) and the wrapper explicitly, to obtain
  a result such as a check.
- A repository may want one practice without the others, and must be able to withdraw one without withdrawing all.

## Decision

1. Nothing in an adopted repository depends on the binary. A clone without it builds, tests, deploys and runs agent
   sessions with the committed configuration.
2. Every entry point the tool writes into a committed file is guarded.
3. An implicit entry point calls the wrapper in a mode that runs only an already cached binary and never downloads
   ([0017](0017-engine-version.md)). When the pinned binary is not cached, it writes one line on stderr and exits
   successfully; the session-start hook also tells the agent, once per session, how to fetch the pinned release.
4. An explicit entry point that cannot run the pinned binary names what is missing and exits non-zero, so an absent
   check is never reported as a passing one.
5. Tool-owned files add no required CI step, no dependency and no change to build, test, deploy or release outputs. A
   team may add the tool's checks to CI as an optional step.
6. Each capability is enabled separately in the repository's data and can be removed on its own
   ([0007](0007-ownership.md)).

## Reasons

- **1.** If committed files depended on the binary, one person's adoption would become a mandate for everyone who
  clones the repository. Rendered configuration is committed ([0007](0007-ownership.md)), so agents read it whether
  or not the binary exists on the machine.
- **2, 3.** A failing hook stops sessions, so an implicit entry point must not fail for lack of the binary. A download
  inside a hook needs the network and cannot fit the 50 ms budget. It would make every first session on a machine slow
  or fragile. Exiting successfully keeps the session going, and the one stderr line tells the person why the hook did
  nothing. The person may never see stderr, while the agent reads its session context, so the one hint per session goes
  there and names the explicit command that fetches the release. The trade is that checks the hook would run are skipped
  on that machine until the binary is cached.
- **4.** An explicit call asks for a result. Exiting zero without running the check would report a pass that never
  happened, which is worse than failing. A non-zero exit that names the missing piece tells the caller exactly what to
  fix. Explicit calls may fetch the pinned release ([0017](0017-engine-version.md)), because someone asked for them.
- **5.** A repository's CI outputs are what its team ships, and adoption must not change them. A required step would
  make the tool a dependency of every pipeline. Teams that want a drift gate add it themselves.
- **6.** Repositories need different subsets of the tool's practices, and withdrawing one practice must not withdraw
  the rest. The manifest records what each capability owns, so removing one is mechanical.

## Rejected alternatives

### Require the binary

Entry points fail when the binary is missing. At its best, every check always runs and nothing is skipped silently. It
loses because it breaks sessions and pipelines for everyone who has not installed the tool, and turns one person's
adoption into a team mandate.

### Implicit entry points that download the pinned release

At its best, hooks work from the first session on a new machine with no manual step. It loses because a hook would
need the network, would break the hook latency budget on its first run, and would add a failure mode that depends on
the network to every agent session.

### A required CI gate in every adopting repository

At its best, drift never reaches the default branch. It loses because it adds a dependency to every pipeline. Teams that
want the gate add it themselves.

### All-or-nothing adoption

At its best, there is one configuration to test. It loses because repositories need different subsets, and
withdrawing one practice must not withdraw the rest.

## Consequences

Costs:

- On a machine without the cached binary, guarded hooks do nothing beyond the stderr line and the one session hint;
  the checks they would run are skipped.
- A teammate who never runs an explicit entry point never gets the binary, so hooks stay inert on their machine.
- Unless a repository adds the check to CI, drift can reach the default branch.

Must be maintained:

- The guard in every entry point, the distinction between implicit and explicit entry points, and the independence of
  capabilities from each other.

## Confirmation

- A CI job adopts each fixture, then runs the fixture's own workflow commands with the binary absent from `PATH` and an
  empty cache, and compares the outputs with those of the unadopted fixture.
- A test runs each rendered implicit entry point with an empty cache and no network, and asserts exit status 0, one
  stderr line and no network access; the session-start hook also emits exactly one line of agent context.
- A test runs each rendered explicit entry point without the binary and asserts a non-zero status and a message naming
  what is missing.
- A fixture with one capability disabled produces no artefact of that capability.
