# 0021. Reviewed engine pins verify package integrity without proving publisher identity

## Objective

**Problem.** A wrapper downloads executable engine content that runs with the person's credentials. A replaced or
corrupted package must not silently run as the pinned release. A checksum can detect a mismatch with the repository's
choice, but cannot establish that the chosen package came from its claimed publisher or is free of malicious code.

**Goal.** Downloads match the repository's reviewed engine pin before execution, and maintainers understand what
that verification proves and what trust remains their responsibility.

## Context

- FR-26 in the [architecture requirements](../architecture.md#requirements) selects JVM distribution using `jpackage`,
  complete platform packages containing Java, a committed wrapper pinning version and checksum, first-use
  download/cache, and explicit upgrades ([0017](0017-engine-version.md)).
- A checksum detects whether downloaded bytes match the recorded pin. If a package was already compromised when its
  checksum was chosen, that checksum will still match. Replacing both a package and an unreviewed checksum at its source
  defeats a comparison with that source; the committed checksum is the reference for subsequent downloads.
- Download transport and publisher identity are different concerns. A download host's identity alone does not prove
  who authored the executable content. Reviewing the wrapper and pin matters because both determine what runs.
- The agreed architecture does not select release signatures, embedded signing keys, key rotation, a trust-bootstrap
  protocol, or an online release health check. Checksum pinning must not be described as providing those mechanisms.
- Recipes are trusted executable Groovy code. Ingredients model native program files and configuration and are held
  and shipped with recipes through cookbook repositories, whose builds can be separate from Cook's. Engine package
  verification does not authenticate those sources or sandbox their evaluation.
- The current [`cook` launcher](../../cook) does not implement release pinning or download verification. The existing
  [jpackage build](../../cli/gradle/jpackage.gradle) creates a local image with Java; it does not establish a published
  release or a working pinned-download wrapper. These decisions describe the intended distribution.

## Decision

1. Review and commit the wrapper, exact engine version, and matching platform-package checksums as executable code
   and dependency choices. Upgrades explicitly change the version and corresponding checksums together.
2. Before executing a downloaded package, compare it with the committed checksum for that version and platform.
   Refuse a mismatch and do not accept that download into the local cache. Cache the verified complete jpackage
   package, including Java, for subsequent invocations ([0017](0017-engine-version.md)).
3. State the trust limit: a matching checksum establishes agreement with the reviewed pin, not publisher identity,
   code safety, or protection from an already compromised package. Reviewers remain responsible for the source and
   package they choose. No signing system is a prerequisite established by this decision.
4. Keep engine integrity separate from recipe trust and file-operation approval. Trust recipe sources before
   evaluation; reviewing the resulting batch diff governs declared writes, not arbitrary recipe side effects.
   Apply requires neither `cook.lock` nor previous history. The committed pin needs no adoption, and ignored `.cook/`
   recovery history is not the only copy of it.
5. Do not use distribution trust to decide recipe/ingredient APIs, nesting, station semantics, decorators, injection,
   annotations, or lifecycle hooks. Those remain separate model questions. Issue #9 supplies review input, not a
   decision to delete dynamic composition or compiled suppliers; this record selects no replacement model API.

## Reasons

- **1, 2.** A committed checksum preserves the repository's chosen package identity independently of what a host later
  serves. Verifying before execution prevents a replaced or corrupted download from running under that choice.
  An explicit pin change exposes a new executable dependency to review instead of silently trusting a fresh checksum
  fetched alongside each download.
- **2.** The trust check covers the distributed package. Verifying and caching only its launcher would omit the Java
  runtime and other content that the chosen JVM distribution needs.
- **3.** Checksums answer an integrity question. Treating them as publisher authentication hides the remaining risk.
  Signatures could address a different part of trust, but choosing keys, verification, rotation, and bootstrap would
  add decisions absent from the agreed architecture.
- **4.** Engine packages and cookbook sources have separate distribution responsibilities. A trusted engine does not
  make every recipe trustworthy, and approval cannot undo effects outside the declared batch. Depending on `cook.lock`
  or adoption gates would add prerequisites unrelated to verifying an engine download.
- **5.** The ingredient concept remains useful program knowledge regardless of release verification. Packaging and
  trust reasoning do not settle how recipes and models compose or how future session capabilities work.

## Alternatives and limits

### Trust each download and its freshly fetched checksum

This avoids committing checksums and keeps download tooling simple. Whoever can replace both files can change what
runs without changing the repository. The committed pin keeps later downloads accountable to the reviewed choice.

### Verify publisher signatures through an already trusted engine

Signed checksum files and embedded public keys could provide publisher authentication distinct from checksum
integrity. They require an initial trust root, key custody, rotation and recovery decisions, and a way to authenticate
pin changes. The agreed design does not choose this protocol; its guarantees must not be promised here.

### Verify signatures in the wrapper or through another signing system

Wrapper signature tooling or a signing system with external trust roots could authenticate a first download. Each
needs its own tooling and trust-bootstrap design. This record does not reject those mechanisms on the basis of an
obsolete Go-only or operating-system-only runtime constraint; it leaves their selection outside the agreed contract.

## Consequences

- A compromised package chosen with its matching checksum still passes verification. A compromised wrapper or an
  unreviewed replacement of the pin can also change what runs. Review and checksum verification have distinct roles.
- Protect cached executable packages from unintended changes. First-download verification does not by itself protect
  a cache from later local modification; no cache revalidation schedule or storage layout is selected here.
- Maintainers need corresponding checksums for the complete platform packages they distribute. This decision adds no
  signed checksum-file format, key list, rotation release, updater command, online doctor check, or mandatory CI gate.
- Cookbook authors retain responsibility for their program models and recipes. The engine pin does not manage recipe
  versions, impose a supplier-admission protocol, or provide a registry.
- Existing or edited target files follow batch diff review and approval. The target CLI has no `--adopt`, adoption
  alias, separate adoption step, or `cook.lock` prerequisite; removing existing gates remains implementation work.

## Confirmation

Review the documented trust flow against these scenarios; no new check command is required:

- A package that differs from the committed checksum cannot execute or become an accepted cache entry.
- Changing the engine version also changes its matching platform checksums for explicit review; ordinary invocations
  keep using the pin rather than trusting whatever the download source currently advertises.
- A matching checksum is described as integrity against the pin, with publisher identity and prior compromise left
  as trust limits. Neither recipe trust nor arbitrary-code sandboxing is inferred from package verification.
- The wrapper and installation references describe the same complete-package flow without promising signatures,
  adoption, or a finalized model API.
