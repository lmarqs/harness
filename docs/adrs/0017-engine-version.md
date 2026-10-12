# 0017. Each repository pins its engine release through a committed wrapper

## Objective

**Problem.** Many people, machines and pipelines work in the same repositories, and the tool's output depends on its
release. When each machine runs whatever release it has, the drift check passes on one machine and fails on another for
the same commit, and a newcomer has to install the right release before anything works.

**Goal.** Every machine and pipeline working in a repository gives the same answer for the same commit, and a newcomer's
first command works with nothing installed beforehand.

## Context

- A person may work across many repositories, with teammates and CI runners working in the same ones. Unless something
  pins the release, a teammate's machine, a CI runner and the release a repository was last rendered with can all
  differ.
- Generated artefacts are committed and drift-checked ([0007](0007-ownership.md)). Rendering rules change between
  releases, so with different releases on different machines, the drift check passes on one machine and fails on
  another for the same commit.
- Build tools pin themselves per repository with a committed wrapper. Gradle's wrapper, as its user guide documents, is
  checked into version control, pins the distribution in a committed properties file, downloads the distribution on
  first use into a per-user directory, and fails the build when an optional configured SHA-256 checksum does not match.
  A clone runs the pinned version without a prior installation of the build tool.
- A newcomer to a repository has installed nothing; the first command they run is the one the repository's
  instructions name.
- Some task runners, such as mise (2026.6), also install tools: their own file holds a tool list that names a version
  for each tool. Repositories differ in their task runners, and some use none ([0004](0004-runtime-dependencies.md)). A
  pin kept in one task runner's tool list does not reach a repository, machine or pipeline without that task runner.
- A version pinned in two places drifts: after the first upgrade that touches only one of them, the two disagree.
- A worker's hooks must not fail or stall a session because the binary is missing
  ([0010](0010-dependence-on-the-binary.md)), and a hook invocation adds at most 50 ms at p95
  ([0003](0003-implementation-language.md)). A download inside a hook breaks both.
- A fresh clone whose hooks find no binary runs worker sessions without the tool's hooks, and the worker has no way to
  know why.
- The tool is one executable with its own templates and documentation embedded
  ([0003](0003-implementation-language.md)); it needs only the operating system at runtime
  ([0004](0004-runtime-dependencies.md)); a repository holds no engine code ([0006](0006-repository-contract.md)).
- Starting a container, even from an image already present, takes a container runtime hundreds of milliseconds; anyone
  with a runtime can time it on their own machine.
- Releases appear while repositories stay on what they pinned; a person learns of a newer release only when something
  tells them.
- Some networks reach only an internal mirror, not the project's release host. The binary contains no client for any
  hosting platform's API ([0012](0012-write-boundary.md)).
- A binary cannot contain its own release's checksums, because they are computed over the binary. Adoption therefore
  fetches them from the release source ([0021](0021-release-trust.md)); a machine that is offline, or reaches neither
  the release host nor a mirror, cannot.

## Decision

1. The default entry point to the tool in a repository is a wrapper script committed at the repository root. The binary
   renders it from a template embedded in itself ([0003](0003-implementation-language.md)) and records it as owned
   ([0007](0007-ownership.md)).
2. The repository pins one exact release in a committed file in the tool configuration directory
   ([0014](0014-directory-layout.md)), together with the SHA-256 checksum of that release's binary for each supported
   platform, or a mark that they are pending (point 13). The pin is owned content recorded in the manifest
   ([0007](0007-ownership.md)), and it exists in exactly one place.
3. A pin is written only by adoption and by an explicit upgrade operation. Its checksums come only from a release
   admitted under [0021](0021-release-trust.md), except that point 13 lets adoption leave them pending. The upgrade
   renders the repository with the new release in the same change, so the pin and every artefact the new release
   renders differently land in one reviewed diff.
4. The wrapper resolves the pinned release in a per-user cache. When the release is absent and the call is explicit,
   the wrapper downloads it over HTTPS, verifies it against the committed checksum, refuses on a mismatch without
   caching anything, and then executes it with the caller's arguments. Once cached, it runs offline.
5. Implicit entry points never download. A worker's hook calls the wrapper in a mode that runs only a cached binary;
   when none is cached, the hook writes one line on stderr naming the wrapper and exits successfully, and the
   session-start hook also tells the worker once per session that the pinned release is not cached on this machine and
   how to fetch it. The wrapper writes both lines itself, because no binary is present to write them. An explicit entry
   point that cannot obtain or verify the pinned binary exits non-zero, so an absent check is never reported as a
   passing one ([0010](0010-dependence-on-the-binary.md)).
6. An environment override makes the wrapper execute a binary at a given path instead, for machines without network
   access and for development of the tool itself.
7. A global installation is optional. Inside a repository whose pin names a different release, a globally installed
   binary executes the pinned release from the cache, and downloads it through the same verified path only when invoked
   explicitly, never from a hook. Outside such repositories it runs itself.
8. The wrapper contains no engine logic: it resolves, downloads, verifies and executes, and when an implicit call finds
   no cached binary it prints the fixed lines of point 5. Every command runs in the binary.
9. The committed pin is the only pin the binary reads. A task runner's tool list is neither read nor edited
   ([0012](0012-write-boundary.md)), and a binary a task runner installs is a global installation (point 7).
10. The binary looks for a release newer than the pin at most once a day, through the release source (point 12),
    caches the answer, never blocks or fails because of the lookup, works offline, and skips the lookup when an
    environment variable disables it. It names a newer release only after verifying that release's signed checksum
    file ([0021](0021-release-trust.md)).
11. CI needs no wrapper ([0010](0010-dependence-on-the-binary.md)). A pipeline that opts in to the tool's checks runs
    them through the wrapper and gets the pinned release, verified.
12. Releases come from the release source the pin names. The source serves each release's assets and signed checksum
    file, and the newest release's signed checksum file, at fixed paths over plain HTTPS. The project's own release
    host and any mirror laid out the same way serve equally, and no platform API is involved
    ([0012](0012-write-boundary.md)).
13. An adoption that cannot reach the release source writes a pending pin when the person asks for one: the release
    and its source, without checksums. An upgrade never writes one. Nothing is downloaded against a pending pin: the
    wrapper and a global installation run the pinned release only when it is already cached, an explicit call that
    would need a download stops and names the step that completes the pin, and implicit entry points behave as for an
    uncached release (point 5). That step is the upgrade operation of point 3 for the pinned release; a global
    installation runs it itself instead of deferring to the pin. The health check reports a pending pin as a finding.

## Reasons

- **1, 4.** Build tools use the same pattern (Context): a committed wrapper is the one entry point every clone has
  without an installation step, so a newcomer runs the command the repository's instructions name and gets the right
  release. Rendering it from a template in the binary keeps it consistent with the release that wrote it, and recording
  it as owned makes a hand edit visible as drift ([0007](0007-ownership.md)). Downloading on first explicit use into a
  per-user cache costs the network once per release per machine; every later run is offline.
- **2.** One exact release per repository makes every machine, every pipeline and the committed artefacts agree, so the
  drift check gives the same answer everywhere. Per-platform checksums let the wrapper verify what it downloads without
  trusting the download host alone. The pin exists in one place because two pins drift apart after the first upgrade
  that touches only one. The pin fixes the engine only: the ingredients and stations a repository uses are pinned
  separately, by content hash, in its data ([0022](0022-tool-integration.md)), so a new version of either and a new
  release of the binary each reach a repository in a change of its own. The pin is owned, not seeded: its checksums are
  worth something only if an admitted release verified them ([0021](0021-release-trust.md)), so a hand edit has to show
  as drift instead of becoming the repository's choice.
- **3.** A new release can change rendering and migrate schemas ([0005](0005-schema-compatibility.md)). An explicit,
  reviewed upgrade lands that change in one diff, instead of whenever a teammate's machine updates. Requiring an
  admitted release ([0021](0021-release-trust.md)) means the committed checksums are ones an already trusted binary
  verified, not whatever the person upgrading happened to download. Rendering with the new release inside the upgrade
  keeps the drift check exact: the pinned release's output and the committed artefacts always agree, so a difference
  is always a real one.
- **5.** A download inside a hook breaks both the no-failure rule of [0010](0010-dependence-on-the-binary.md) and the
  50 ms budget of [0003](0003-implementation-language.md). Without a hint, a fresh clone's worker works without the
  tool's hooks and cannot tell why; one line in its context per session tells it the fix, and limiting it to one line
  keeps the cost in tokens and noise fixed. Only the wrapper is present at that moment, so only the wrapper can say it.
- **6.** A machine without network access, or a person developing the tool, needs to run a binary that no pin names.
  An environment override is explicit for one environment and leaves the committed pin unchanged.
- **7.** People who install globally keep using the short command. Deferring to the pin keeps the one-release guarantee
  of point 2 for them as well; never downloading from a hook follows point 5.
- **8.** A repository holds no engine code ([0006](0006-repository-contract.md)). Resolving, downloading, verifying
  and executing is the minimum a clone needs to reach the binary; any further logic in the wrapper would be engine code
  copied into every repository, beyond the reach of a release. The lines of point 5 are fixed text in the wrapper's
  template, selected by the event the hook names; printing them reports a failed resolution and decides nothing.
- **9.** Reading a task runner's file would tie the binary to that task runner's format, and the binary reads no
  program's own configuration ([0022](0022-tool-integration.md)). Nothing is lost by not reading it: a binary the task
  runner installs defers to the committed pin inside the repository (point 7), so a second version in the task runner's
  list changes nothing the repository renders or checks. The trade: that version can drift from the pin unreported, and
  keeping it is the team's choice.
- **10.** Without a notice, a repository stays on its pinned release indefinitely. The lookup must never cost a session
  or a pipeline anything, so it is cached, non-blocking and offline-safe, and it can be disabled for CI and machines
  without network access.
- **11.** CI stays free of any requirement ([0010](0010-dependence-on-the-binary.md)); a pipeline that chooses to run
  the checks gets the same verified release as every developer.
- **12.** Some networks reach only an internal mirror. Fixed paths over HTTPS make a mirror a plain copy of files, keep
  the wrapper a downloader rather than an API client, and keep platform logic out of the binary
  ([0012](0012-write-boundary.md)). Serving the newest release's signed checksum file at a fixed path lets the update
  notice learn about a release and verify it in one request, so a compromised host cannot advertise an unsigned
  release.
- **13.** A binary cannot carry its own checksums, so an adoption that cannot reach the release source cannot verify
  them. Refusing such an adoption would block it on every machine that is offline or reaches neither the release host
  nor a mirror, although nothing else in adoption needs the network. A pending pin defers the verification without
  skipping it. No download happens against it, so it fails closed: the cost is availability on machines that lack the
  release, never an unverified binary. A release already in the cache was verified against a committed checksum when
  it was downloaded (point 4), so running it under a pending pin trusts nothing new. Writing a pending pin only on
  request keeps a network failure from producing, unnoticed, a pin that other machines cannot use. Completing it through
  the upgrade operation keeps one operation that writes checksums. Every committed checksum is then one a trusted binary
  verified ([0021](0021-release-trust.md)). A global installation runs that operation itself because deferring to a
  release nobody can obtain would leave the pin with no way to be completed. The binary that runs it is one the person
  already trusts, and it verifies the release with its embedded keys before writing. An upgrade never pends because the
  repository keeps its verified pin while it waits for the network. Reporting a pending pin as a finding lets a pipeline
  that runs the health check stop a pin that other machines cannot use.

## Rejected alternatives

### Global installation only

One installation per machine serves every repository. At its best, no repository carries a script or a pin, a release
costs no change in any repository, and no first run needs the network. It loses because releases skew between machines,
pipelines and the rendered repository, so the drift check passes on one machine and fails on another. A teammate with an
older binary is refused by a newer schema ([0005](0005-schema-compatibility.md)) until they update, every CI job chooses
its own release, and a newcomer installs the tool before the first command works.

### Pin through a task runner's tool list

The repository lists the tool and its version in the tool list of the task runner it already uses, and the task runner
installs and selects that version. At its best, there is no new script and no new file, and the pin sits beside the
repository's other tool versions. It loses because the pin reaches only machines where that task runner is installed,
which excludes repositories that use another task runner or none ([0004](0004-runtime-dependencies.md)). A worker's
hooks get the pinned version only when the task runner's activation is present in the worker's environment, and pinning
depends on a third-party tool.

### Vendored binary in the repository

The repository commits the binary for each platform. At its best, a clone runs the pinned version offline from its first
command, with no download, cache or checksum step. It loses because every release adds four platform binaries
([0003](0003-implementation-language.md)) to the permanent history of every repository. A change to a binary cannot be
reviewed as a diff, and the repository carries engine content ([0006](0006-repository-contract.md)).

### Container image

The repository names an image that contains the pinned binary, and entry points run the tool inside it. At its best, the
image pins the binary and its environment together, and CI systems that run jobs in images use it directly. It loses
because every machine needs a container runtime ([0004](0004-runtime-dependencies.md)). A container start takes hundreds
of milliseconds against the 50 ms hook budget ([0003](0003-implementation-language.md)), and the tool must reach the
working tree and the version control state from inside the container.

### Refuse adoption without access to the release source

At its best, every committed pin carries verified checksums from its first commit, and no pin exists that other
machines cannot use. It loses because adoption then depends on the network at the moment a person adopts, for the one
step that needs it, while a pending pin keeps the guarantee the checksums exist for: no download runs unverified.

### Seed the cache with the adopting binary

An offline adoption copies the running binary into the per-user cache as the pinned release. At its best, the adopting
machine's hooks and wrapper work at once, with no network. It loses because every other cache entry was verified
against a signed checksum, and the cache is shared by every repository on the machine: the unverified entry would run
as the pinned release in repositories whose pins were verified.

### Complete the pin on the first online command

Any explicit command that finds the release source reachable fills in the checksums. At its best, nobody has to
remember a step. It loses because an unrelated command would rewrite a committed, security-relevant file as a side
effect, and the checksums would reach the repository outside the one operation that writes every other pin.

## Consequences

Costs:

- The first explicit run on a machine needs network access, a POSIX shell, a downloader and a checksum tool. These are
  requirements of the wrapper, not runtime dependencies of the binary ([0004](0004-runtime-dependencies.md)).
- Each release reaches a repository only through a change to its pin. The change is generated and can be produced for
  many repositories in one operation, but each repository reviews its own.
- A teammate who never runs an explicit entry point gets nothing from the tool's hooks beyond the stderr line and the
  session hint ([0010](0010-dependence-on-the-binary.md)).
- The environment override skips the checksum; what runs is whatever the person placed at that path. The override,
  and a global binary too old to defer to pins, are the two ways to run another release inside a pinned repository.
- The repository carries one tool-owned script; a hand edit to it is drift ([0007](0007-ownership.md)).
- A committed pending pin leaves every machine that lacks the pinned release in its cache unable to run explicit entry
  points until someone completes the pin.
- Windows is not served ([0003](0003-implementation-language.md)).

Must be maintained:

- The wrapper's portability across POSIX shells, downloaders and checksum tools on Linux and macOS, and its scope:
  resolve, download, verify, execute.
- A published checksum for every platform binary of every release ([0021](0021-release-trust.md)).
- The hook budget of [0003](0003-implementation-language.md), measured through the wrapper.

## Confirmation

- A test runs the wrapper with an empty cache against a release fixture and asserts that it verifies the checksum,
  caches the binary and executes it with the given arguments; with a wrong checksum it exits non-zero and caches
  nothing.
- A test runs every rendered implicit entry point with an empty cache and no network, and asserts a successful exit, one
  stderr line naming the wrapper, no network access, and exactly one line of session context from the session-start
  hook.
- A test runs a global binary inside a fixture pinned to another release and asserts that the pinned release runs, and
  that a hook invocation never downloads.
- A fixture that also lists the tool in its task runner's tool list renders, checks and adopts exactly as the same
  fixture without it, and the task runner's file is byte-identical afterwards.
- A test asserts that the release lookup returns promptly offline and makes no network call when disabled, and that a
  newest-release checksum file with a bad signature produces no notice.
- A fixture pinned to a mirror laid out like the release host downloads, verifies and runs the release with no other
  network access.
- An upgrade fixture produces one diff holding the pin and every artefact the new release renders differently, and the
  drift check run by the new release passes on it.
- An offline adoption fixture writes a pending pin and makes no network access. With an empty cache, an explicit call
  exits non-zero naming the completing step and downloads nothing, a hook exits 0 as for an uncached release, and the
  health check reports a finding. The upgrade operation for the pinned release completes the pin, and its diff touches
  only the pin and the pin's manifest entry.
- An upgrade fixture whose release source is unreachable writes nothing.
- CI runs the wrapper tests on Linux and macOS with each supported downloader and checksum tool.
- The hook benchmark of [0003](0003-implementation-language.md) runs through the wrapper and fails above 50 ms at p95.
