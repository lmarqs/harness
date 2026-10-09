# 0003. The tool is one executable written in Go

## Objective

**Problem.** The tool runs on every developer machine and CI runner that adopts it, and inside agent hooks that fire on
every file edit. A language whose programs start slowly, or need a runtime a machine lacks, makes every agent action
slower or makes hooks fail. The language is also the hardest part of the tool to change later.

**Goal.** A hook call stays within 50 ms at p95, and the tool runs on every supported machine with nothing else
installed, for the life of the project.

## Context

- The tool runs on developer machines, on CI runners, and inside hook calls that harnesses fire on agent events such as
  every file edit. Hook latency adds to every agent action, and a session can make hundreds of edits.
- Across 51 surveyed repositories, 36 pin no Python version and the rest span Python 3.9 to 3.14. Many have no Node.
- The developer machines and CI runners in scope run Linux or macOS, on amd64 or arm64.
- The maintainer's other distributed tools are Go binaries with a working cross-compilation and release pipeline.
- The workflow this tool generalises is written in Python and has contract tests.
- The language is the hardest part of the tool to change: changing it is a rewrite.

## Decision

1. The tool is one executable written in Go, cross-compiled for Linux and macOS on amd64 and arm64.
2. Documentation, the shipped skill, stack defaults and templates are embedded at build time. There are no helper
   scripts, plugins or sidecar processes.
3. A hook invocation adds at most 50 ms at p95 on a developer workstation, excluding the repository's own commands. A
   change that exceeds this budget is not released.

## Reasons

- **1.** Go compiles to a native executable that needs no runtime to install or start, which satisfies
  [0004](0004-runtime-dependencies.md) and the hook budget of point 3 by construction. The toolchain cross-compiles to
  the four targets without extra tooling, and the release pipeline of the maintainer's other tools applies unchanged,
  so release cost is known before the first release. The trade: the Python workflow and its tests are rewritten.
- **2.** One file is one thing to pin, download, verify and cache ([0017](0017-engine-version.md),
  [0021](0021-release-trust.md)). Embedded documentation and templates ship with the release that enforces them
  ([0002](0002-division-of-work.md)). Helper scripts or plugins beside the binary would be engine code that can drift
  or be edited, which [0006](0006-repository-contract.md) excludes.
- **3.** Hook overhead multiplies by the number of agent events in a session. A fixed budget measured in CI makes
  latency a release criterion instead of a regression that people discover in their sessions.

## Rejected alternatives

### Python, distributed as a zip application or an isolated install

At its best, there is no rewrite: the tested Python code of the workflow this tool generalises is reused as is. It loses
because every machine and CI runner needs a compatible interpreter, the survey shows interpreter skew across
repositories, and it contradicts [0004](0004-runtime-dependencies.md).

### TypeScript compiled into one executable with an embedded JavaScript runtime

At its best, it uses the language of many target repositories and some adjacent tools, and ships as one file. It loses
because the executable embeds a full JavaScript runtime whose start-up is paid on every hook call against the 50 ms
budget, the rewrite costs the same as in Go, and no release pipeline exists for it.

### Rust

At its best, a native executable with the same start-up and static-linking properties as Go, and stronger compile-time
guarantees. It loses because it adds a second toolchain and release pipeline beside the maintainer's existing Go ones
for no property this tool needs: the work is file rendering, hashing and process supervision, which Go covers.

## Consequences

Costs:

- The logic and contract tests of the Python workflow are rewritten in Go; a subtle invariant can be lost in the
  translation.
- Contributors need Go. Repository-specific validators stay in whatever language each repository uses.
- Windows is not a target platform.

Must be maintained:

- The cross-platform build matrix and a reproducible release.
- The hook latency benchmark.

## Confirmation

- Release CI produces the four platform binaries.
- CI benchmarks a hook invocation through the wrapper and fails above 50 ms at p95.
- Every invariant in [0011](0011-safety-invariants.md) maps to a contract test written before or with the code that
  enforces it.
