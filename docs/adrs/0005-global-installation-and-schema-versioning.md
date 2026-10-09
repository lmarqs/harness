# 0005. Global installation by default; version skew is handled by schema versioning and an update notice

- Status: Accepted
- Date: 2026-10-09

## Context

- One person may work across about thirty repositories. A version pinned in each repository turns every release
  into a version-bump change in each of them, which in practice needs an update bot.
- A globally installed binary skews: a teammate's machine, a CI runner and the version a repository was last
  rendered with can all differ.
- Generated artefacts are committed and checked for drift ([0007](0007-recorded-ownership-and-committed-artefacts.md)).
  If they recorded the binary version, every upgrade would show as drift.
- Per-repository pinning was the first recommendation for this project. The maintainer chose global installation
  and accepted version skew as its cost.

## Decision

1. The binary is installed once per machine, through a package manager or a release download, and serves every
   repository on that machine.
2. A repository may pin a version through its own tool-version manager. The tool neither requires nor prevents it.
3. Compatibility is carried by an integer schema version in each repository file, not by the binary version. The
   binary refuses a file whose schema is newer than it supports, with a message naming the update needed. It
   migrates a file with an older schema and shows the diff. Generated artefacts record the schema version and never
   the binary version.
4. The binary looks for a newer release at most once a day, caches the answer, never blocks or fails because of the
   lookup, works offline, and skips the lookup when an environment variable disables it.

## Alternatives considered

### Mandatory pinning per repository

At its best, CI is reproducible and versions never skew. Not chosen: each release costs a change in every
repository; each repository needs setup before first use; and it makes the tool a declared dependency of
repositories that adopt it optionally ([0010](0010-optional-and-transparent-adoption.md)).

### Automatic self-update

At its best, every machine runs the latest version. Not chosen: a tool that writes committed files would change
behaviour silently, need the network, and surprise CI.

### No version contract

The latest binary is assumed everywhere. Not chosen: an old binary could rewrite a newer repository's files with
outdated rules.

## Consequences

Positive:

- No per-repository setup; one update per machine.
- Upgrading the binary never shows as drift because of a version stamp.

Negative:

- A teammate with an older binary cannot render a repository with a newer schema until they update.
- A CI job that runs a check installs the binary and chooses its version.
- A schema change is a migration in every adopting repository, so schema changes must be rare.

Must be maintained:

- Migrations from every supported older schema, and a documented support window.
- This decision is revisited at the first incident in which version skew causes a wrong write.

## Confirmation

- Fixtures at the previous schema migrate and print a diff; fixtures at a newer schema are refused with a
  dedicated exit code and a message.
- A test asserts that no generated artefact contains the binary version.
- A test asserts that the release lookup returns promptly offline and makes no network call when disabled.
