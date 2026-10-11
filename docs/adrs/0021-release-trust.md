# 0021. Only an already trusted binary admits downloaded content

## Objective

**Problem.** The tool runs content it downloads with the person's credentials, on every machine and in every repository
that pins it: the release the wrapper script committed in each repository downloads, the ingredients the repository
pins, whose commands the binary starts, the stations it applies, whose entries become its own commands and rules, and
the recipes it copies, whose steps it runs. A release compromised before anyone recorded its checksum, or replaced on
the download host, would run everywhere, and checksums alone cannot tell. An ingredient or a station replaced at its
source, or fetched while nobody watches, would change what a worker runs without any change to the repository, and a
recipe replaced at its source would enter the repository as if its cookbook had published it.

**Goal.** Only releases their publisher signed run, on every machine, whoever recorded the pin, an ingredient or a
station acts only as the exact bytes a reviewed change to the repository pinned, and a recipe enters a repository only
as the bytes its cookbook published, through a reviewed change.

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
- An ingredient describes commands the binary starts, such as the launch of a worker and the version control operations
  ([0022](0022-tool-integration.md)), so its content decides what runs with the person's credentials, as a release's
  does. A station's entries, lifecycle actions and deny rules among them, become the repository's own data when it is
  applied, and a station lists the ingredients it uses and the recipes it adds, each with its content hash
  ([0024](0024-ingredients-and-stations.md)).
- The binary ships no ingredient, no station and no recipe ([0022](0022-tool-integration.md)). All are published in
  cookbooks by organizations and people other than the tool's publisher, and a binary can embed only the keys known when
  it is built.
- Ingredients, stations and recipes are data: a reviewer can read all of one before pinning or copying it, which a
  compiled binary does not allow.
- A recipe's command steps run with the person's credentials. Once copied, a recipe is a file of the repository that its
  team edits, and every change to it is a change to the repository, reviewed like any other
  ([0013](0013-organization-content.md), [0025](0025-recipes.md)).
- A per-user cache can be written by every process the person runs.
- A hook runs on every matching worker event, and an attempt runs with nobody watching
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
7. An ingredient or a station is admitted when a person pins it. The binary downloads, over HTTPS, the cookbook's
   checksum file for the pinned version and each ingredient or station the person names, verifies each against that
   file, and presents the pins as a diff that enters the repository only through review
   ([0022](0022-tool-integration.md)). An ingredient that a station lists is pinned in the same diff, at the content
   hash the station gives, unless the repository already pins that ingredient, whose pin is kept and the difference
   reported, and it is verified against its pinned hash when it is fetched ([0024](0024-ingredients-and-stations.md)).
   From then on the binary verifies every ingredient and station against the content hash the repository's data pins, on
   every download and every load, and refuses content that does not match before anything it describes runs or is
   written. It fetches one only on a person's explicit command, never during a hook or an attempt. No release key
   vouches for any ingredient or station, and the wrapper never fetches or loads one.
8. A recipe is admitted when a person adds it, updates it or applies a station that adds it. When a person adds or
   updates it, the binary downloads, over HTTPS, the cookbook's checksum file for the version named and the recipe, and
   verifies the recipe against that file. A recipe a station adds is verified against the content hash the station gives
   for it, as an ingredient the station lists is, so applying the station again never trusts a checksum file downloaded
   again. The binary presents the copy, or the merge, as a diff that enters the repository only through review
   ([0013](0013-organization-content.md)). No content hash pins the copy the repository holds: it is the repository's
   own file, and it acts as the repository holds it. The origin the copy records names the cookbook, the version and the
   content hash of the recipe it came from; an update takes that version, cached or downloaded, as the common ancestor
   of its merge and verifies it against that hash. The binary fetches a recipe only on a person's explicit command,
   never during a hook, an attempt or a recipe run, and no release key vouches for one.

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
- **7.** An ingredient's commands run with the person's credentials, as a release's do, and a station's entries become
  the repository's own commands and rules (Context), so the bytes of both need the same guarantee: what acts is exactly
  what someone pinned. A release key vouches only for the release's publisher, and the author of an ingredient or a
  station is someone else, whose key no binary can carry (Context), so trust in them is built in three steps. At pin
  time, the cookbook's checksum file over HTTPS is what ties the downloaded bytes to what the cookbook published; HTTPS
  authenticates only the host (Context), so this step alone is the model rejected below for releases as checksums
  recorded by whoever upgrades. The second step closes most of that gap: the pins enter the repository only through a
  reviewed diff, and an ingredient or a station is data a reviewer can read in full before approving it (Context), which
  a compiled release does not allow. An ingredient a station lists takes its hash from a station the checksum file
  verified, and its pin appears in the same diff for the same review. After that, the hash the reviewed change committed
  is the root, and verifying it on every load catches a cached copy changed after it was fetched (Context). Fetching
  only on a person's command makes the moment of trust a moment a person acts: a fetch during a hook or an attempt would
  let whoever controls the source change what a worker runs while nobody watches. The wrapper stays a shell script that
  knows only the release (point 4). The trade: an ingredient or a station compromised at its source before someone
  pinned it passes every check, and because none comes from the release ([0022](0022-tool-integration.md)), the floor of
  every program is only as good as the review of its pin.
- **8.** A recipe's steps run with the person's credentials (Context), so the bytes that enter the repository need the
  guarantee of point 7 at that moment: the cookbook's checksum file ties them to what the cookbook published. A recipe a
  station adds takes its hash from the station, whose own bytes are verified as point 7 decides, as an ingredient a
  station lists does: applying the station again then copies exactly the recipe the station was published with, whatever
  the checksum file at its source says by then. After that, a recipe differs from an ingredient. The copy is in the
  repository, a reviewer reads all of it in the diff that adds it, and every later change to it is a reviewed change
  like any other (Context). A hash pin would turn each of the team's edits into a mismatch, so the copy's trust rests on
  the review of the changes that hold it, as the entries a station writes do. The origin's hash serves the merge:
  verifying the version an update starts from means the merge compares against exactly the bytes that were copied, so
  the diff it shows separates the team's changes from the cookbook's. Fetching only on a person's command keeps a
  recipe's source out of every moment nobody watches, as point 7 does for ingredients and stations. The trade: a recipe
  compromised at its source before it was added passes every check, and the review of its diff is the whole defence.

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

### Signed ingredients and stations

The author of each ingredient and station signs it, and the binary verifies the signature as it verifies a release's. At
its best, an ingredient or a station compromised at its source before someone pinned it is caught. It loses because the
binary would need every author's key: embedding them couples each release to those authors, the coupling
[0013](0013-organization-content.md) rejects for organization content, and fetching them trusts the host the signature
exists to distrust (point 2).

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
- Every ingredient and station, and with them the floor of every program, is only as good as the review of its pin: one
  compromised at its source before it was pinned passes every check.
- A recipe is only as good as the review of the diff that adds or updates it, and no hash catches a later change to the
  copy: every such change is the repository's own and goes through its review.
- Every machine, CI included, fetches each pinned ingredient in an explicit step before a hook or an attempt can use it.

Must be maintained:

- A signed checksum file for every release, custody of the signing key, rotation releases, and the embedded key list.
- Verification of every ingredient and station a person names against the cookbook's checksum file when it is pinned, of
  every ingredient a station lists against the hash the station gives, and of each against its pin on every download and
  every load.
- Verification of every recipe added or updated against the cookbook's checksum file, of every recipe a station adds
  against the hash the station gives, and of an update's common ancestor against the hash its origin records.

## Confirmation

- Release CI fails when the checksum file or its signature is missing, or when the signature does not verify with the
  keys embedded in the binary being released and in the release before it.
- An upgrade fixture against a release with an invalid signature is refused, and the pin stays byte-identical.
- A release fixture signed by a key that an earlier release introduced verifies with a binary that trusts both keys.
- The wrapper refuses a tampered asset fixture ([0017](0017-engine-version.md)).
- A fixture whose committed checksum differs from the signed checksum file is reported by the health check.
- A test asserts that signature verification runs inside the binary and starts no external program.
- A pin fixture whose downloaded ingredient or station differs from the cookbook's checksum file is refused, and the
  repository's files stay byte-identical.
- A pin fixture against a cookbook source that is not HTTPS is refused before any download.
- A fixture ingredient whose cached bytes differ from the hash its repository pins is refused on load, and no command it
  describes starts; a fixture station whose cached bytes differ from its pin is refused, and applying it writes nothing.
- A pin fixture of a station pins the ingredients it lists at the hashes it gives, in the same diff.
- A fixture hook and a fixture attempt that need an uncached ingredient make no network request.
- An add fixture whose downloaded recipe differs from the cookbook's checksum file is refused, and the repository's
  files stay byte-identical.
- Applying a fixture station whose recipe differs from the hash the station gives is refused, and writes nothing, even
  when a checksum file downloaded again lists the changed recipe.
- An update fixture whose cached common ancestor differs from the hash its origin records is refused, and the copy stays
  byte-identical.
- A fixture recipe edited after it was added runs as edited, and nothing compares it with its origin.
