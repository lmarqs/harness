# 0021. Only an already trusted binary admits downloaded content

## Objective

**Problem.** The tool runs content it downloads with the person's credentials, on every machine and in every repository
that pins it: the release the wrapper script committed in each repository downloads, and recipes from outside sources,
whose commands the binary starts. A release compromised before anyone recorded its checksum, or replaced on the download
host, would run everywhere, and checksums alone cannot tell. A recipe replaced at its source, or fetched while nobody
watches, would change what an agent runs without any change to the repository.

**Goal.** Only releases their publisher signed run, on every machine, whoever recorded the pin, and a recipe from an
outside source acts only as the exact bytes a reviewed change to the repository pinned.

## Context

- The wrapper downloads and executes the pinned release on every machine that runs an explicit entry point
  ([0017](0017-engine-version.md)). A compromised release runs with the person's credentials, in every repository that
  pins it.
- A checksum protects against a change in transit and against a release replaced after its checksum was recorded. It
  does not protect against a release that was already compromised when someone recorded its checksum: with checksums
  alone, whoever records the pin trusts whatever they downloaded.
- HTTPS authenticates the download host, not the author of a release. Whoever can replace assets on the host replaces
  the release.
- The wrapper is a POSIX shell script. Verifying a signature from such a script needs a tool such as gpg or a recent
  OpenSSL, whose presence and version differ between machines.
- The Go standard library verifies Ed25519 signatures (the `crypto/ed25519` package, since Go 1.13), so the binary can
  verify signatures with no runtime dependency ([0003](0003-implementation-language.md),
  [0004](0004-runtime-dependencies.md)).
- Every change to a pin is a committed diff that the repository reviews ([0017](0017-engine-version.md)).
- Signing keys are rotated, and can be lost or compromised.
- A recipe describes commands the binary starts, such as the launch of an agent and the version control operations
  ([0022](0022-tool-integration.md)), so its content decides what runs with the person's credentials, as a release's
  does.
- Recipes from outside sources are written by organizations and people other than the tool's publisher. A binary can
  embed only the keys known when it is built.
- A recipe is data: a reviewer can read all of it before pinning it, which a compiled binary does not allow.
- A per-user cache can be written by every process the person runs.
- A hook runs on every matching agent event, and an unattended attempt runs with nobody watching
  ([0011](0011-safety-invariants.md)).

## Decision

1. Every release publishes a checksum file covering each platform asset, and a detached signature over that file.
2. Every binary embeds the release public keys it trusts. A release signed by the current key introduces the next key,
   and later binaries trust both until a release retires the old one.
3. Checksums enter a pin, at adoption and at upgrade, only after a running binary verifies the release's signed
   checksum file with its embedded keys. A release that fails verification is refused, and nothing is written. A pin
   without checksums admits no download ([0017](0017-engine-version.md)).
4. The wrapper verifies only the downloaded asset's checksum against the committed pin. Signatures are verified only by
   binaries.
5. The trust root is the first binary a person installs.
6. The health check, when online, re-verifies the pinned release's signed checksum file against the committed
   checksums.
7. A recipe from an outside source is admitted by the binary against the content hash the repository's data pins
   ([0022](0022-tool-integration.md)). The binary verifies that hash on every load and refuses content that does not
   match before anything the recipe describes runs, and it fetches such a recipe only on a person's explicit command,
   never during a hook or an unattended attempt. No release key vouches for such a recipe, and the wrapper never fetches
   or loads one.

## Reasons

- **1.** One signature over the checksum file covers every platform asset, and the checksums are exactly what the pin
  records ([0017](0017-engine-version.md)), so the signature extends to everything the wrapper later checks.
- **2.** Embedded keys need no fetch at verification time. Fetching them from the release host would trust the same host
  the signature exists to distrust. Introducing the next key in a release signed by the current one moves trust forward
  without a gap, and the overlap lets binaries of both generations verify the releases between them.
- **3.** This moves the trust decision from whatever the person upgrading downloaded to what a binary already trusted
  verified, which closes the gap that checksums alone leave. Writing nothing on failure keeps the repository on its last
  verified pin, and every accepted change still arrives as a diff for review. A pin without checksums gives the wrapper
  nothing to compare a download with, so it admits none: verification can be deferred, never skipped.
- **4.** A shell script cannot verify signatures portably, and the binary can with no runtime dependency
  ([0004](0004-runtime-dependencies.md)). Because a verifying binary wrote the committed checksums (point 3), the
  wrapper's checksum comparison carries that verification over to each download.
- **5.** Some first binary has to be trusted without a prior one. Installing it from the release page over HTTPS or
  through a package manager is the one step that rests on host authentication alone. Every later release is admitted by
  a verified binary.
- **6.** A checksum edited by hand in the pin, or a checksum file replaced on the host after the pin was written, is
  found. Running online only, and as part of the health check, costs nothing to a machine without network access.
- **7.** A recipe's commands run with the person's credentials, as a release's do (Context), so its bytes need the same
  guarantee: what acts is exactly what someone pinned. A release key vouches only for the release's publisher, and a
  recipe's author is someone else, whose key no binary can carry (Context). The strongest root left is the hash a
  reviewed change committed, which is the model rejected below for releases as checksums recorded by whoever upgrades.
  It is accepted for recipes because a recipe is data a reviewer can read in full before pinning it (Context), and
  because the other choice is no recipes from outside sources at all, which leaves a program no release describes
  unusable ([0022](0022-tool-integration.md)). Verifying on every load catches a cached copy changed after it was
  fetched (Context). Fetching only on a person's command makes the moment of trust a moment a person acts: a fetch
  during a hook or an unattended attempt would let whoever controls the source change what an agent runs while nobody
  watches. The wrapper stays a shell script that knows only the release (point 4). The trade: a recipe compromised at
  its source before someone pinned it passes every check, which is why the health check names it as not verified by the
  release and why it cannot take the name of a recipe the release ships ([0022](0022-tool-integration.md)).

## Rejected alternatives

### Checksums recorded by whoever upgrades

At its best, there are no keys to manage and releasing is simplest. It loses because the first download is trusted as
it is: a release compromised before its checksum was recorded passes every later check.

### Keyless signing with a public transparency log

At its best, there is no long-lived key to protect, and every signature leaves a public audit trail. It loses because
verification needs a large verification library or an external tool, and depends on trust roots and log state that live
outside the release. Neither fits a shell wrapper or a binary limited to the operating system
([0004](0004-runtime-dependencies.md)).

### OpenPGP signatures

At its best, the format is widely known and many people already have tooling for it. It loses because verification in
the wrapper needs gpg on every machine ([0004](0004-runtime-dependencies.md)), and in the binary it adds a large format
for no gain over a plain signature with embedded keys.

### HTTPS alone

At its best, nothing beyond the download is needed. It loses because a release replaced on the host passes.

### Signed recipes

Each recipe's author signs it, and the binary verifies the signature as it verifies a release's. At its best, a recipe
compromised at its source before someone pinned it is caught. It loses because the binary would need every author's key:
embedding them couples each release to those authors, the coupling [0013](0013-organization-content.md) rejects for
organization content, and fetching them trusts the host the signature exists to distrust (point 2).

### Signature verification in the wrapper

At its best, even a first download is checked against a signature, without relying on the committed checksum. It loses
because the wrapper would need a signature tool on every machine, which is the dependency the binary avoids
([0004](0004-runtime-dependencies.md)).

## Consequences

Costs:

- A compromised signing key signs releases that every binary trusting it accepts until a release retires the key.
  Binaries that are never upgraded keep trusting it.
- A lost signing key cannot introduce its successor, so people install a new binary as a new trust root.
- The first installation rests on host authentication alone.
- The wrapper checks the downloaded asset against the committed checksum, not the signature. A checksum edited by hand
  in the pin passes the wrapper and is caught by the drift check ([0007](0007-ownership.md)) and the health check.
- The release process holds a signing key that must be protected.
- A recipe from an outside source is only as trustworthy as the review of its pin: one compromised at its source before
  it was pinned passes every check.
- Every machine, CI included, fetches each pinned recipe from an outside source in an explicit step before a hook or an
  attempt can use it.

Must be maintained:

- A signed checksum file for every release, custody of the signing key, rotation releases, and the embedded key list.
- Hash verification of every recipe from an outside source on every load.

## Confirmation

- Release CI fails when the checksum file or its signature is missing, or when the signature does not verify with the
  keys embedded in the binary being released and in the release before it.
- An upgrade fixture against a release with an invalid signature is refused, and the pin stays byte-identical.
- A release fixture signed by a key that an earlier release introduced verifies with a binary that trusts both keys.
- The wrapper refuses a tampered asset fixture ([0017](0017-engine-version.md)).
- A fixture whose committed checksum differs from the signed checksum file is reported by the health check.
- A test asserts that signature verification runs inside the binary and starts no external program.
- A fixture recipe from an outside source whose cached bytes differ from the hash its repository pins is refused on
  load, and no command it describes starts.
- A fixture hook and a fixture unattended attempt that need an uncached recipe make no network request.
