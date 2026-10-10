# 0004. The binary needs only the operating system and git at runtime

## Objective

**Problem.** Every runtime the binary needs is one more thing each teammate and pipeline must install and keep at the
right version, including people who never chose the tool. A missing or mismatched runtime shows up as failing hooks
inside agent sessions.

**Goal.** Adopting the tool asks nothing of a machine beyond what it already has for working with git repositories, and
no hook fails for lack of a runtime.

## Context

- Adoption must cost nothing to teammates and pipelines that do not use the tool
  ([0010](0010-dependence-on-the-binary.md)) and little to those who do.
- Across 51 surveyed repositories, 36 pin no Python and the rest span 3.9 to 3.14; 12 do not use the task runner the
  others share; several have no Node.
- Hooks run on every matching agent event. A missing or mismatched interpreter surfaces as failing hooks inside agent
  sessions.
- Some adjacent work belongs to third-party tools that bring their own runtime, such as a skills installer that needs
  Node.
- Everything the tool manages is a git repository: commits, branches, working copies and diffs. git is present wherever
  a repository is cloned.
- macOS supports system calls only through its system library, so every macOS program links it.
- On a machine where no binary exists yet, the wrapper obtains the pinned release ([0017](0017-engine-version.md)).

## Decision

1. The binary needs nothing at runtime besides the operating system and git. It neither requires nor bundles an
   interpreter, a language runtime or a package manager.
2. On Linux the binary is statically linked; on macOS it links only the system library.
3. Work owned by a third-party tool with its own runtime is delegated to that tool when the tool is present. When it is
   absent, the binary prints the command to run and reports the item as pending; it never installs a runtime.
4. Commands the repository declares and harnesses the tool starts belong to the repository and the user; they are not
   dependencies of the binary.
5. The wrapper's first-run requirements, a POSIX shell, a downloader and a checksum tool, are requirements of the
   wrapper ([0017](0017-engine-version.md)), not of the binary.

## Reasons

- **1.** The survey shows no runtime that every repository shares. Requiring one turns adoption into an installation
  project and adds a setup step to every CI job that runs a check. A mismatched interpreter breaks hooks inside agent
  sessions. git is the one program the tool cannot work without and that every clone already has. The trade: git is a
  hard dependency.
- **2.** A statically linked Linux binary runs on every distribution and in minimal containers, so CI can use it by
  downloading one file. On macOS the system library is the only supported way to reach the kernel, so linking it adds
  no dependency a macOS machine lacks.
- **3.** Reimplementing a third-party tool duplicates a specification another project owns and changes. Delegation keeps
  that behaviour correct as the third-party tool evolves. Reporting the item as pending, instead of installing a
  runtime, leaves each machine's runtimes to its owner. The trade: those capabilities stay pending on machines without
  the runtime.
- **4.** The repository's commands are its own data ([0006](0006-repository-contract.md)) and the harnesses are the
  user's choice ([0001](0001-role-of-the-tool.md)). Counting them as the binary's dependencies would make its
  requirements unbounded.
- **5.** The wrapper runs before any binary exists on the machine, so it can rely only on what a POSIX system provides.
  Once the binary runs, it uses none of those tools, so point 1 stays true of the binary.

## Rejected alternatives

### Require a documented runtime

For example, a minimum Python version. At its best, the code is smaller and contribution is easier. It loses because the
survey shows version skew, and every CI pipeline that runs a check gains a setup step.

### Bundle an interpreter

At its best, the host needs nothing and a scripting language stays available. It loses because the artefact is larger,
interpreter start-up is paid on every hook, and two languages are maintained.

### Reimplement third-party work natively

For example, reimplement the skills installer to avoid Node. At its best, nothing is ever pending for lack of a runtime.
It loses because it duplicates a specification another project owns and changes.

## Consequences

Costs:

- Capabilities delegated to third-party tools stay pending on machines without their runtime.
- git is a hard dependency.
- Libraries that need cgo or spawn external programs are excluded, which narrows library choice.

Must be maintained:

- Review of every new library for cgo use and for spawning external programs.

## Confirmation

- CI runs the binary's check commands in a container that holds only git and the binary.
- Release CI asserts that the Linux binaries are statically linked and that the macOS binaries link only the system
  library.
- Fixture tests assert that, apart from git, the binary executes only commands the fixture repository declares and
  harnesses it was asked to start.
