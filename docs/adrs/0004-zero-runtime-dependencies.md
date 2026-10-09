# 0004. The binary has zero runtime dependencies

- Status: Accepted
- Date: 2026-10-09

## Context

- Adoption must cost nothing to teammates and pipelines that do not use the tool
  ([0010](0010-optional-and-transparent-adoption.md)) and little to those who do.
- Across 51 surveyed repositories, 36 pin no Python and the rest span 3.9 to 3.14; 12 do not use the task runner the
  others share; several have no Node.
- Hooks run on every matching agent event. A missing or mismatched interpreter surfaces as failing hooks inside
  agent sessions.
- Some adjacent work belongs to third-party tools that bring their own runtime, such as a skills installer that
  needs Node.

## Decision

The binary needs nothing at runtime besides the operating system and git. It neither requires nor bundles an
interpreter, a language runtime or a package manager. On Linux it is statically linked; on macOS it links only
the system library every macOS program links.

Work owned by a third-party tool with its own runtime is delegated to that tool when the tool is present. When it
is absent, the binary prints the command to run and reports the item as pending; it never installs a runtime.

Commands the repository declares and the harnesses the tool starts belong to the repository and the user; they are
not dependencies of the binary.

## Alternatives considered

### Require a documented runtime

For example, a minimum Python version. At its best, the code is smaller and contribution is easier. Not chosen:
the survey shows version skew, and every CI pipeline that runs a check would gain a setup step.

### Bundle an interpreter

At its best, the host needs nothing and the scripting language stays. Not chosen: a larger artefact, interpreter
startup on every hook, and two languages to maintain.

### Reimplement third-party work natively

For example, reimplement the skills installer to avoid Node. At its best, nothing is ever pending for lack of a
runtime. Not chosen: it duplicates a specification another project owns and changes.

## Consequences

Positive:

- Installing is copying one file; a CI job uses the tool by downloading one file.
- No interpreter skew can break a hook.

Negative:

- Capabilities delegated to third-party tools stay pending on machines without their runtime.
- git is a hard dependency.

Must be maintained:

- Review of every new library for cgo use or for spawning external programs.

## Confirmation

- CI runs the binary's check commands in a container that holds only git and the binary.
- Release CI asserts that the Linux binaries are statically linked and that the macOS binaries link only the
  system library.
- Fixture tests assert that, apart from git, the binary executes only commands the fixture repository declares.
