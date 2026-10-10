# 0004. The binary needs only the operating system at runtime

## Objective

**Problem.** Every runtime the binary needs is one more thing each teammate and pipeline must install and keep at the
right version, including people who never chose the tool. A missing or mismatched runtime shows up as failing hooks
inside agent sessions.

**Goal.** Adopting the tool asks nothing of a machine beyond what it already has for working with its repositories, and
no hook fails for lack of a runtime.

## Context

- Adoption must cost nothing to teammates and pipelines that do not use the tool
  ([0010](0010-dependence-on-the-binary.md)) and little to those who do.
- Repositories carry the runtimes and tools of their own stack, so what one repository has, another lacks. Example: at
  acme, `acme/api` is a Node service with no Python, two Python services pin Python 3.9 and 3.13, and `acme/infra` holds
  Terraform code, has neither Python nor Node, and uses no task runner, while the other repositories share one.
- Hooks run on every matching agent event. A missing or mismatched interpreter surfaces as failing hooks inside agent
  sessions.
- Some adjacent work belongs to third-party tools that bring their own runtime, such as the `skills` CLI (1.7), a skills
  installer that needs Node.
- Everything the tool manages lives in a version control system: commits, branches, working copies and diffs. The
  version control program, git in nearly every repository, is present wherever a repository is cloned, and the tool
  reaches it through the version control plugin ([0022](0022-tool-integration.md)).
- macOS supports system calls only through its system library, so every macOS program links it.
- On a machine where no binary exists yet, the wrapper, a script committed in the repository, obtains the pinned release
  ([0017](0017-engine-version.md)).

## Decision

1. The binary needs nothing at runtime besides the operating system. It neither requires nor bundles an interpreter, a
   language runtime or a package manager. Every program it starts is named by a plugin, a declared command or an action
   ([0022](0022-tool-integration.md)), and is a requirement of that plugin or that repository, not of the binary.
2. On Linux the binary is statically linked; on macOS it links only the system library.
3. Work owned by a third-party tool with its own runtime stays with that tool: the binary neither reimplements it nor
   runs the tool of its own accord, and never installs a runtime. The tool runs when a person runs it or when a plugin,
   a command or an action the repository uses names it (point 4, [0022](0022-tool-integration.md)).
4. Commands the repository declares, programs its plugins name and harnesses the tool starts belong to the repository
   and the user; they are not dependencies of the binary. The health check reports each one that is missing.
5. The wrapper's first-run requirements, a POSIX shell, a downloader and a checksum tool, are requirements of the
   wrapper ([0017](0017-engine-version.md)), not of the binary.

## Reasons

- **1.** A repository's runtimes follow its stack, so no runtime is common to every repository (Context). Requiring one
  turns adoption into an installation project and adds a setup step to every CI job that runs a check. A mismatched
  interpreter breaks hooks inside agent sessions. The version control program is one the tool cannot work without, but
  it is the repository's choice, described by a plugin, and every clone already has it. The trade: the binary cannot
  do anything useful in a repository until its version control plugin's program is installed, and says so.
- **2.** A statically linked Linux binary runs on every distribution and in minimal containers, so CI can use it by
  downloading one file. On macOS the system library is the only supported way to reach the kernel, so linking it adds
  no dependency a macOS machine lacks.
- **3.** Reimplementing a third-party tool duplicates a specification another project owns and changes. Leaving the work
  with the tool keeps that behaviour correct as the tool evolves, and running it only where the repository declares it
  keeps the tool's runtime off the binary's list of needs (point 4). The trade: work such as installing shared skills is
  a step the team takes with that tool, on a machine that has its runtime.
- **4.** The repository's commands are its own data ([0006](0006-repository-contract.md)), and the programs its plugins
  describe, the harnesses and the version control system included, are the team's choice
  ([0022](0022-tool-integration.md)). Counting them as the binary's dependencies would make its requirements unbounded.
  Reporting the missing ones turns a failure in the middle of a command into a finding before it.
- **5.** The wrapper runs before any binary exists on the machine, so it can rely only on what a POSIX system provides.
  Once the binary runs, it uses none of those tools, so point 1 stays true of the binary.

## Rejected alternatives

### Require a documented runtime

For example, a minimum Python version. At its best, the code is smaller and contribution is easier. It loses because
repositories pin different versions of the same runtime, or none (Context), and every CI pipeline that runs a check
gains a setup step.

### Bundle an interpreter

At its best, the host needs nothing and a scripting language stays available. It loses because the artefact is larger,
interpreter start-up is paid on every hook, and two languages are maintained.

### Reimplement third-party work natively

For example, reimplement the skills installer to avoid Node. At its best, nothing is ever pending for lack of a runtime.
It loses because it duplicates a specification another project owns and changes.

## Consequences

Costs:

- Work that belongs to a third-party tool, such as installing shared skills, needs that tool and its runtime on the
  machine where the team runs it.
- Every command that reads the repository needs the version control plugin's program, which the binary does not
  provide.
- Libraries that need cgo or spawn external programs are excluded, which narrows library choice.

Must be maintained:

- Review of every new library for cgo use and for spawning external programs.

## Confirmation

- CI runs the binary's check commands in a container that holds only the binary, the wrapper's first-run tools and the
  programs the fixture's plugins name, and runs the core's contract tests in a container that holds only the binary's
  test executable ([0023](0023-core-and-contracts.md)).
- Release CI asserts that the Linux binaries are statically linked and that the macOS binaries link only the system
  library.
- Fixture tests assert that the binary executes only programs that the fixture's plugins, declared commands and actions
  name.
