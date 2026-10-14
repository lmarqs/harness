# 0021. Reviewed engine pins verify package integrity without proving publisher identity

## Objective

**Problem.** A repository's entry point downloads executable engine content that runs with the person's credentials.
A replaced or corrupted download must not silently run as the release the repository chose. Detecting a mismatch with
that choice does not establish that the chosen package came from its claimed publisher or is free of malicious code.

**Goal.** What runs matches the engine release the repository reviewed, and maintainers understand what that check
proves and what trust remains their responsibility.

## Context

- FR-26 in the [architecture requirements](../architecture.md#requirements) selects JVM distribution using `jpackage`,
  complete platform packages containing Java, a committed wrapper pinning version and checksum, first-use
  download/cache, and explicit upgrades ([0017](0017-engine-version.md)).
- A checksum detects whether downloaded bytes match the recorded pin. If a package was already compromised when its
  checksum was chosen, that checksum will still match. Replacing both a package and an unreviewed checksum at its source
  defeats a comparison with that source; the committed checksum is the reference for subsequent downloads.
- Download transport and publisher identity are different concerns. A download host's identity alone does not prove
  who authored the executable content. Reviewing the wrapper and pin matters because both determine what runs.
- Recipes are trusted executable Groovy code. [Ingredients](../features/glossary.md) model native program files and
  configuration and are held and shipped with recipes through cookbook repositories, whose builds can be separate from
  Cook's. Engine package verification does not authenticate those sources or sandbox their evaluation.

## Decision

1. The wrapper, exact engine version, and matching platform-package checksums are reviewed and committed as
   executable code and dependency choices. Upgrades explicitly change the version and corresponding checksums
   together.
2. Before executing a downloaded package, the wrapper compares it with the committed checksum for that version and
   platform. A mismatch is refused and never accepted into the local cache. The verified complete jpackage package,
   including Java, is cached for subsequent invocations ([0017](0017-engine-version.md)).
3. A matching checksum establishes agreement with the reviewed pin, not publisher identity, code safety, or protection
   from an already compromised package. Reviewers remain responsible for the source and package they choose.
4. Engine integrity is separate from recipe trust and file-operation approval ([0026](0026-cookbook-trust.md)).

## Reasons

- **1, 2.** A committed checksum preserves the repository's chosen package identity independently of what a host later
  serves. Verifying before execution prevents a replaced or corrupted download from running under that choice.
  An explicit pin change exposes a new executable dependency to review instead of silently trusting a fresh checksum
  fetched alongside each download. The trust check covers the distributed package: verifying and caching only its
  launcher would omit the Java runtime and other content that the chosen JVM distribution needs.
- **3.** Checksums answer an integrity question. Treating them as publisher authentication hides the remaining risk.
- **4.** Engine packages and cookbook sources have separate distribution responsibilities. A trusted engine does not
  make every recipe trustworthy, and approval cannot undo effects outside the declared batch.

## Rejected alternatives

### Trust each download and its freshly fetched checksum

At its best, this avoids committing checksums and keeps download tooling simple. It loses because whoever can replace
both files can change what runs without changing the repository. The committed pin keeps later downloads accountable
to the reviewed choice.

### Verify publisher signatures

At its best, signed checksum files with embedded public keys, signature tooling in the wrapper, or a signing system
with external trust roots authenticate the publisher, including on a first download, beyond checksum integrity. It
loses because each needs an initial trust root, key custody, rotation and recovery decisions, and a way to
authenticate pin changes, none of which the agreed architecture chooses. Promising their guarantees without those
decisions would overstate what verification proves.

## Consequences

Costs:

- A compromised package chosen with its matching checksum still passes verification. A compromised wrapper or an
  unreviewed replacement of the pin can also change what runs. Review and checksum verification have distinct roles.
- First-download verification does not by itself protect a cache from later local modification.

Must be maintained:

- Cached executable packages are protected from unintended changes.
- Maintainers publish corresponding checksums for the complete platform packages they distribute.
- Cookbook authors retain responsibility for their program models and recipes; the engine pin does not manage recipe
  versions.

## Confirmation

- Can a package that differs from the committed checksum execute or become an accepted cache entry? It must not.
- Does changing the engine version also change its matching platform checksums for explicit review, while ordinary
  invocations keep using the pin rather than whatever the download source currently advertises?
- Does the [wrapper](../features/wrapper.md) page describe a matching checksum as integrity against the pin, with
  publisher identity and prior compromise left as trust limits, and the complete-package flow without promising
  signatures?
