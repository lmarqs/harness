# 0003. The tool is a single binary written in Go

- Status: Accepted
- Date: 2026-10-09

## Context

- The tool runs in three settings: developer machines, CI runners, and inside hook calls that harnesses fire on
  agent events such as every file edit. Hook latency adds to every agent action.
- The incumbent implementation is about 1,200 lines of Python with about 1,000 lines of contract tests.
- Across 51 surveyed repositories, 36 pin no Python version and the rest span Python 3.9 to 3.14.
- The maintainer's other distributed tools are Go binaries with an existing cross-compilation and release pipeline.
- The language is the hardest decision in this project to reverse: changing it later is a rewrite.

## Decision

The tool is one executable written in Go, cross-compiled for Linux and macOS on amd64 and arm64. The binary is
statically linked on Linux and links only the system library on macOS
([0004](0004-zero-runtime-dependencies.md)). Documentation, the shipped skill, stack defaults and
templates are embedded at build time. There are no helper scripts, plugins or sidecar processes.

A hook invocation adds at most 50 ms at p95 on a developer workstation, excluding the repository's own commands.
This budget is a design requirement: a change that exceeds it is not released.

## Alternatives considered

### Keep Python, distributed as a zip application or an isolated install

At its best, there is no rewrite and the tested code is reused as is. Not chosen: every machine and CI runner needs
a compatible interpreter, the survey shows interpreter skew across repositories, and it contradicts
[0004](0004-zero-runtime-dependencies.md).

### TypeScript compiled into one executable with an embedded JavaScript runtime

At its best, it uses the same language as many target repositories and some adjacent tools, and ships as one file.
Not chosen: the executable embeds a full JavaScript runtime whose startup is paid on every hook call against the
50 ms budget; the rewrite cost equals Go's; and no release pipeline exists for it.

## Consequences

Positive:

- One artefact per platform, with no runtime to start, which keeps hook calls within the latency budget.
- The build and release pipeline matches the maintainer's other tools.

Negative:

- The incumbent logic and its tests are rewritten; a subtle invariant can be lost in the rewrite.
- Contributors need Go. Repository-specific validators stay in whatever language each repository uses.
- Windows is not a target platform.

Must be maintained:

- The cross-platform build matrix and a reproducible release.

## Confirmation

- Release CI produces the four platform binaries.
- CI benchmarks a hook invocation and fails above 50 ms at p95.
- Every invariant in [0011](0011-built-in-safety-invariants.md) maps to a contract test written before or with the
  code that enforces it.
