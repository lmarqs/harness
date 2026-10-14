# 0026. A cookbook's content is admitted by a reviewed pin

## Objective

**Problem.** Cookbooks publish content the tool runs with the person's credentials, in every repository that takes it
up: the ingredients a repository pins, whose commands the binary starts, the stations it applies, whose entries become
its own commands and rules, and the recipes it copies, whose steps it runs. An ingredient or a station replaced at its
source, or fetched while nobody watches, would change what a worker runs without any change to the repository, and a
recipe replaced at its source would enter the repository as if its cookbook had published it.

**Goal.** An ingredient or a station acts only as the exact bytes a reviewed change to the repository pinned, and a
recipe enters a repository only as the bytes its cookbook published, through a reviewed change.

## Context

- An ingredient describes commands the binary starts, such as the launch of a worker and the version control operations
  ([0022](0022-tool-integration.md)), so its content decides what runs with the person's credentials, as a release's
  does. A station's entries, lifecycle actions and deny rules among them, become the repository's own data when it is
  applied, and a station lists the ingredients it uses and the recipes it adds, each with its content hash
  ([0024](0024-ingredients-and-stations.md)).
- A recipe's command steps run with the person's credentials. Once copied, a recipe is a file of the repository that its
  team edits, and every change to it is a change to the repository, reviewed like any other
  ([0025](0025-recipes.md)).
- The binary ships no ingredient, no station and no recipe ([0022](0022-tool-integration.md)). All are published in
  cookbooks by organizations and people other than the tool's publisher, and a binary can embed only the keys known when
  it is built.
- A cookbook publishes what it holds by version, with a checksum file that lists the content hash of each unit
  ([0024](0024-ingredients-and-stations.md)).
- Engine package checksums verify integrity against a reviewed pin; they do not prove publisher identity
  ([0021](0021-release-trust.md)). Engine verification does not authenticate cookbook content.
- HTTPS authenticates the download host, not the author of what it serves. Whoever can replace assets on a cookbook's
  host replaces the checksum file with them.
- Ingredients, stations and recipes are data: a reviewer can read all of one before pinning or copying it, which a
  compiled binary does not allow.
- A per-user cache can be written by every process the person runs.
- A hook runs on every matching worker event, and an attempt runs with nobody watching.

## Decision

1. An ingredient or a station is admitted when a person pins it. The binary downloads, over HTTPS, the cookbook's
   checksum file for the pinned version and each ingredient or station the person names, verifies each against that
   file, and presents the pins as a diff that enters the repository only through review
   ([0022](0022-tool-integration.md)). An ingredient that a station lists is pinned in the same diff, at the content
   hash the station gives, unless the repository already pins that ingredient, whose pin is kept and the difference
   reported, and it is verified against its pinned hash when it is fetched ([0024](0024-ingredients-and-stations.md)).
   From then on the binary verifies every ingredient and station against the content hash the repository's data pins, on
   every download and every load, and refuses content that does not match before anything it describes runs or is
   written. It fetches one only on a person's explicit command, never during a hook or an attempt. No release key
   vouches for any ingredient or station, and the wrapper never fetches or loads one.
2. A recipe is admitted when a person adds it, updates it or applies a station that adds it. When a person adds or
   updates it, the binary downloads, over HTTPS, the cookbook's checksum file for the version named and the recipe, and
   verifies the recipe against that file. A recipe a station adds is verified against the content hash the station gives
   for it, as an ingredient the station lists is, so applying the station again never trusts a checksum file downloaded
   again. The binary presents the copy, or the merge, as a diff that enters the repository only through review. No
   content hash pins the copy the repository holds: it is the repository's
   own file, and it acts as the repository holds it. The origin the copy records names the cookbook, the version and the
   content hash of the recipe it came from; an update takes that version, cached or downloaded, as the common ancestor
   of its merge and verifies it against that hash. The binary fetches a recipe only on a person's explicit command,
   never during a hook, an attempt or a recipe run, and no release key vouches for one.

## Reasons

- **1.** An ingredient's commands run with the person's credentials, as a release's do, and a station's entries become
  the repository's own commands and rules (Context), so the bytes of both need the same guarantee: what acts is exactly
  what someone pinned. A release signature would vouch for the release's publisher, not automatically for cookbook
  authors; engine signing is not selected by [0021](0021-release-trust.md). Trust in them is built in three steps.
  First, at pin time, the cookbook's checksum file over HTTPS ties the downloaded bytes to what the cookbook published.
  HTTPS authenticates only the host (Context); checksum matching alone does not prove publisher identity. Second, the pins
  enter the repository only through a reviewed diff, and an ingredient or a station is data a reviewer can read in full
  before approving it (Context), which a compiled release does not allow. An ingredient a
  station lists takes its hash from a station the checksum file verified, and its pin appears in the same diff for the
  same review. Third, after that, the hash the reviewed change committed is the root, and verifying it on every load
  catches a cached copy changed after it was fetched (Context). Fetching only on a person's command makes the moment of
  trust a moment a person acts: a fetch during a hook or an attempt would let whoever controls the source change what a
  worker runs while nobody watches (Context). The wrapper resolves and verifies the engine package, independently of
  cookbook content ([0017](0017-engine-version.md)). The trade: an ingredient or a station compromised at its source
  before someone pinned it passes every check, and because none comes from the release ([0022](0022-tool-integration.md)), the floor of every
  program is only as good as the review of its pin.
- **2.** A recipe's steps run with the person's credentials (Context), so the bytes that enter the repository need the
  guarantee of point 1 at that moment: the cookbook's checksum file ties them to what the cookbook published. A recipe a
  station adds takes its hash from the station, whose own bytes are verified as point 1 decides, as an ingredient a
  station lists does: applying the station again then copies exactly the recipe the station was published with, whatever
  the checksum file at its source says by then. After that, a recipe differs from an ingredient. The copy is in the
  repository, a reviewer reads all of it in the diff that adds it, and every later change to it is a reviewed change
  like any other (Context). A hash pin would turn each of the team's edits into a mismatch, so the copy's trust rests on
  the review of the changes that hold it, as the entries a station writes do. The origin's hash serves the merge:
  verifying the version an update starts from means the merge compares against exactly the bytes that were copied, so
  the diff it shows separates the team's changes from the cookbook's. Fetching only on a person's command keeps a
  recipe's source out of every moment nobody watches, as point 1 does for ingredients and stations. The trade: a recipe
  compromised at its source before it was added passes every check, and the review of its diff is the whole defence.

## Rejected alternatives

### Signed ingredients and stations

The author of each ingredient and station signs it, and the binary verifies the signature. At its best, an ingredient
or a station compromised at its source before someone pinned it is caught. It loses because the binary would need
every author's key: embedding them couples each release to those authors, and fetching them trusts the host the
signature exists to distrust. Engine signing and embedded keys are not selected by
[0021](0021-release-trust.md).

### The cookbook's checksum file at every download

The repository's data names a cookbook and a version, and every download is verified against the cookbook's checksum
file as it is downloaded then. At its best, no hash has to be kept in the repository's data. It loses because whoever
can replace assets on the cookbook's host replaces the checksum file with them (Context): what acts would change with no
change to the repository, which the goal excludes, and applying a station again would copy whatever recipe the
checksum file lists by then.

### A content hash on a recipe's copy

The copy a repository holds is pinned by its content hash and verified on every load, as an ingredient is. At its best,
a change to the copy that bypasses the repository's review is caught. It loses because the copy is a file of the
repository that its team edits, and every change to it is already reviewed like any other (Context): each of the team's
edits would be a mismatch to clear with a new hash, and the hash would add no check the review does not already make.

## Consequences

Costs:

- Every ingredient and station, and with them the floor of every program, is only as good as the review of its pin: one
  compromised at its source before it was pinned passes every check.
- A recipe is only as good as the review of the diff that adds or updates it, and no hash catches a later change to the
  copy: every such change is the repository's own and goes through its review.
- Every machine, CI included, fetches each pinned ingredient in an explicit step before a hook or an attempt can use it.

Must be maintained:

- Verification of every ingredient and station a person names against the cookbook's checksum file when it is pinned, of
  every ingredient a station lists against the hash the station gives, and of each against its pin on every download and
  every load.
- Verification of every recipe added or updated against the cookbook's checksum file, of every recipe a station adds
  against the hash the station gives, and of an update's common ancestor against the hash its origin records.

## Confirmation

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
