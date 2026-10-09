# 0010. Adoption is optional and transparent: nothing in a repository depends on the binary being present

- Status: Accepted
- Date: 2026-10-09

## Context

- Not every teammate, machine or pipeline will install the tool, yet everyone who clones a repository shares its
  committed files.
- 34 of 51 surveyed repositories run hosted CI workflows. Their build, test, deploy and release outputs must not
  change because a repository adopted the tool.
- Hooks run inside agent sessions. A hook that fails interrupts every session of every teammate.
- A repository may want one practice without the others, and must be able to withdraw one without withdrawing all
  ([0007](0007-recorded-ownership-and-committed-artefacts.md)).

## Decision

- Nothing in an adopted repository depends on the binary. A clone without it builds, tests, deploys and runs agent
  sessions with the committed configuration.
- Every entry point the tool writes into a committed file is guarded. An entry point that agents call implicitly
  (an agent hook) exits successfully after one line on stderr when the binary is not installed. An entry point a
  person or agent calls explicitly to obtain a result (a task-runner wrapper) names the missing binary and exits
  non-zero, so an absent check is never reported as a passing one.
- Tool-owned files add no required CI step, no dependency and no change to build, test, deploy or release outputs.
  A team may add the tool's checks to CI as an optional step.
- Each capability is enabled separately in the repository's data and can be removed on its own.

## Alternatives considered

### Require the binary

Entry points fail when it is missing. At its best, every check always runs. Not chosen: it breaks sessions and
pipelines for everyone who has not installed the tool and turns one person's adoption into a team mandate.

### A required CI gate in every adopting repository

At its best, drift never reaches the default branch. Not chosen: it adds a dependency to every pipeline; teams that
want the gate add it themselves.

### All-or-nothing adoption

At its best, there is one configuration to test. Not chosen: repositories need different subsets, and withdrawing
one practice must not withdraw the rest.

## Consequences

Positive:

- One person's adoption imposes nothing on teammates or pipelines.
- A repository can take one practice at a time.

Negative:

- Without the binary, guarded hooks do nothing beyond the stderr line; checks they would run are skipped.
- Unless a repository adds the check to CI, drift can reach the default branch.

Must be maintained:

- The guard in every entry point and the independence of capabilities from each other.

## Confirmation

- A CI job adopts each fixture, then runs the fixture's own workflow commands with the binary absent from `PATH` and
  compares the outputs with those of the unadopted fixture.
- A test runs each rendered entry point without the binary and asserts exit status 0 and one stderr line for
  implicit entry points, and a non-zero status naming the binary for explicit ones.
- A fixture with one capability disabled produces no artefact of that capability.
