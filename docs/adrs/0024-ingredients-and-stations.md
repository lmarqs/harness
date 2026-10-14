# 0024. An ingredient describes one program, a station one way of working

## Objective

**Problem.** Teams take two kinds of shared content from outside their repositories: descriptions of the programs
their lifecycle reaches, which must hold on every render and follow each program's releases, and starting content for a
way of working, which a team takes once and then edits as its own. When one unit carries both, a pin means two things
at once, part of it acting on every render and part of it applied once, so a reader cannot tell from the repository
what still acts on it. A way of working combines several programs, and with one unit per program it has no unit of its
own: its wiring is split across the programs' descriptions or carried by one of them.

**Goal.** Everything a repository takes from a shared source either acts on every render as a program's description or
becomes the repository's own content once applied, and a reader of the repository can tell which. A way of working
that combines several programs reaches a repository in one reviewed change.

## Context

- What a repository needs from a program follows that program and has to hold on every render: the files and keys the
  program reads, the arguments that start it, the arguments that bypass its checks, the paths in its hook payload
  ([0022](0022-tool-integration.md)). When a deny rule that protects a program, such as one on a force push to the
  default branch, is an entry the repository owns, a repository that never applies it, or edits it away, runs without
  it ([0011](0011-safety-invariants.md)).
- What a repository needs from a way of working is starting content that the team then owns and edits: the commands
  and lanes of its stack, its validators, which workers it uses in which role, the deny rules for unattended work, the
  lifecycle actions that update a tracker or open a pull request, standing-context entries, and whole files such as a
  loop runner's configuration ([0013](0013-organization-content.md)).
- One program serves several ways of working, and one way of working combines several programs. Example: at acme,
  `acme/api` works with git, a coding agent used interactively, a second coding agent that runs unattended through a
  loop runner, and a task tracker that its lifecycle actions update. `acme/infra` uses git and the first coding agent,
  interactively only, with no loop runner and no tracker. Both need git and that coding agent described the same way;
  they differ in the lanes, the unattended deny rules and the lifecycle actions they start from.
- A technology stack's starting content is commands, lanes and post-edit hooks, which a repository declares as its own
  data. Example: `acme/api` and `acme/web` lint, test and build the same Node way.
  A stack whose commands go through a task runner also relies on the deny rule against chaining several tasks in one
  invocation of that task runner ([0011](0011-safety-invariants.md)).
- ESLint and semantic-release each have two units of extension. ESLint's plugins add rules, and its shareable
  configurations are packages that export a configuration choosing and setting rules. semantic-release's plugins carry
  out release steps, and its shareable configurations package a set of options. In both, a project's configuration
  names a shareable configuration, the tool loads it on every run, and the project's own settings override it.
- Copier, a project template tool, generates a project from a template once and writes into the project a file that
  records which template and which version generated it, so that a later update can apply a newer version of the
  template.
- A cookbook publishes what it holds by version, with a content hash for each unit, and a repository pins what it uses
  through a reviewed change ([0022](0022-tool-integration.md), [0026](0026-cookbook-trust.md)).
- Organization choices and stack facts reach a repository by being applied once: only what is missing is added, every
  difference is reported, and every entry and file is the repository's own afterwards
  ([0013](0013-organization-content.md)).
- A way of working can rely on a kind of change it makes the same way in every repository that uses it. Example: in
  `acme/api`, the loop runner makes the same kind of change, implementing one tracker task, for every task, and its
  configuration names that kind of change ([0025](0025-recipes.md)).

## Decision

1. A cookbook publishes ingredients and stations, the two units this decision separates, and recipes
   ([0025](0025-recipes.md)), each named, versioned and covered by a content hash ([0022](0022-tool-integration.md)).
2. An ingredient describes one program: the operations that fill one contract or none
   ([0023](0023-core-and-contracts.md)), its renders, its floor and its checks ([0022](0022-tool-integration.md)). It
   holds no entries and no templates. While a repository pins it, its renders and its floor act on every render.
3. A station describes one way of running the lifecycle ([0015](0015-development-lifecycle.md)). It lists the
   ingredients it uses, each pinned by cookbook, version and content hash. It assigns them to the lifecycle: which one
   fills the version control contract, and which workers it declares, each with its ingredient and its role
   ([0009](0009-agent-support.md)). It holds entries for the repository's data, templates, and optionally a detect rule
   that tells adoption the station fits the repository. It holds no renders and no floor. A station may list no
   ingredient, such as one for a technology stack whose content is commands, lanes and hooks. It may also pin recipes
   from its own cookbook version that it adds to the repository, each by its content hash, as it pins its ingredients.
4. A station is applied once, on a person's request. Its entries and templates become the repository's own under the
   rules of [0013](0013-organization-content.md): applying only adds, the repository's entries win, and every difference
   is reported. The ingredients it lists are pinned into the repository's data in the same reviewed change. An
   ingredient the repository already pins at another content hash keeps the repository's pin, and the difference is
   reported. Each recipe it pins is verified against the content hash the station gives and copied into the repository
   as adding that recipe copies it ([0013](0013-organization-content.md)), in the same reviewed change; a recipe the
   repository already holds is kept and reported, never overwritten.
5. The repository's data lists the ingredients it pins and, apart from them, the stations it applied, each with its pin.
   What the binary renders and runs depends only on the ingredients. The list of applied stations records which stations
   the repository started from, and at which version, so that a person can apply a newer version later. It has no
   effect on what is rendered or run, and removing an entry from it changes nothing rendered. A name appears at most
   once in each list.
6. A repository may apply several stations, such as one for a way of working with workers and one for each technology
   stack it uses. Each is applied by the rules of point 4, so what an earlier station wrote is the repository's own when
   a later one is applied.

## Reasons

- **1.** The two kinds of content differ in when they act (Context): a program's side of the repository has to follow
  the program and hold on every render, while a way of working is starting content the team owns once it has it. A unit
  for each keeps every pin meaning one thing. The linter and the release tool in Context draw the same line between the
  unit that adds a capability and the unit that chooses how to use it. The trade: two formats to version
  ([0005](0005-schema-compatibility.md)) and to learn, and a cookbook author decides for each piece of content which
  unit it belongs to.
- **2.** What a repository writes once about a program depends on how its team works, not on the program: `acme/api`
  and `acme/infra` need one coding agent described the same way and start from different lanes, deny rules and actions
  (Context). Without entries and templates, one ingredient serves both, and the difference is in what each repository
  applied. Everything an ingredient holds acts while it is pinned and stops when it is not, so the
  pin alone says what the ingredient does to the repository ([0007](0007-ownership.md)).
- **3.** A way of working is a combination of programs and repository facts (Context), so it needs a unit that names
  all of them. Listing each ingredient by hash ties the station to the exact program descriptions its entries were
  written against, and assigning them to the version control contract and to worker roles states the combination in the
  terms of the lifecycle it runs. A station holds no renders and no floor because its content is the repository's own
  once applied (point 4), and because a floor belongs to the program it protects ([0022](0022-tool-integration.md)): in
  a station it would protect only the repositories that applied that station, not every repository that uses the
  program. A stack's content is commands the repository declares (Context), so a station for it may list no ingredient,
  or only the ingredient of the task runner whose chaining deny its commands rely on.
- **4.** Applying once follows [0013](0013-organization-content.md): the entries are readable in the repository's own
  files and stay authoritative, and a second application is harmless. Pinning the listed ingredients in the same change
  puts a way of working and the program descriptions it relies on into one diff, so a reviewer reads the whole effect at
  once, and a repository never holds a station's entries without the ingredients they need. Keeping an existing pin
  applies the rule for entries to pins: a repository that pins a newer version control ingredient keeps it when it
  applies a station written against an older one. Reporting the difference shows that the station's entries meet an
  ingredient version the station did not list. Copying the recipes a station lists in the same change means its entries,
  such as a loop runner's configuration that runs a recipe for each task, never name a recipe the repository lacks
  (Context). Pinning each recipe by content hash, as the station pins its ingredients, ties the station to the exact
  recipes its entries were written against, and applying it again copies those bytes, verified against the station's own
  hash rather than a checksum file downloaded again ([0026](0026-cookbook-trust.md)). A recipe the repository already
  holds is kept for the reason an entry already present is: the repository's version is the one its team chose
  ([0013](0013-organization-content.md)). The trade: that combination is one the station's cookbook did not test, and a
  kept recipe may differ from the one the station's entries were written against.
- **5.** What is rendered and run depends only on the ingredients, so once a station is applied it acts only through
  what it wrote into the repository's files, which keeps the rule of [0013](0013-organization-content.md) that only the
  repository's files and what they pin by hash act on it. Recording which stations were applied, and at which version,
  tells a reader where the repository's starting content came from and gives a person the version to compare with when a
  newer one appears, as a project template tool's record of its template does (Context). The record lists stations, not
  entries, so it ties no entry to its station and gives the tool nothing it could use to change an entry by itself
  ([0013](0013-organization-content.md)). A name declared twice among the ingredients would make the description that
  fills a contract depend on order ([0022](0022-tool-integration.md)), and twice among the applied stations would record
  two versions of one starting point. The trade: the record states what was applied, not what the repository still
  holds.
- **6.** One repository can use several stacks ([0013](0013-organization-content.md)), and its way of working with
  workers is independent of its stacks (Context). Applying each station by the same rules means a later station never
  replaces what an earlier one wrote. The trade: where two stations propose different values for one entry, the first
  applied stays, and the second's value is only reported.

## Rejected alternatives

### One unit per program, with entries and renders together

Each program has one description holding its operations, renders, floor and checks, and the entries and templates a
repository starts from. At its best, one pin adds a program together with its wiring, and one page documents
everything about it. It loses because the wiring is not the program's: the lanes, deny rules and lifecycle actions of
`acme/api` and `acme/infra` differ while the descriptions of their programs do not (Context), so one team's way of
working would ship as a program's description. A way of working that combines several programs would have no unit of
its own, and its wiring would sit in one of the programs' descriptions. The pin would also mean two things: removing it
removes the renders and leaves the entries it applied, so a reader cannot tell from the pin what still acts.

### Stations inherited at every render

The repository's data names the stations it uses, and the binary merges their entries into every render, as the linter
and the release tool in Context load a shareable configuration on every run. At its best, a way of working improved once
reaches every repository at its next pin move, with no second application. It loses for the reasons
[0013](0013-organization-content.md) gives against shared sources resolved at every render: an entry acts from a source
no reader of the repository sees, merging needs override rules a person must learn before predicting what a repository
does, and each repository still reviews every move, so only the writing is saved.

### A station that renders

A station holds renders and floor rules of its own, such as an organization's deny rules for unattended work, rendered
on every render while the repository lists the station. At its best, a way of working can enforce rules a repository
cannot edit away. It loses because the station would then act on every render, which brings back the inheritance of the
alternative above for those rules, and the list of applied stations would become a dependency instead of a record. A
deny that protects a program belongs in that program's ingredient, where it reaches every repository that uses the
program ([0022](0022-tool-integration.md)), and an organization that wants a rule of its own as floor publishes it in
the ingredient of the program the rule protects ([0013](0013-organization-content.md)). A rule that is a team's choice
is policy, which the repository owns ([0011](0011-safety-invariants.md)).

## Consequences

Costs:

- A cookbook publishes two kinds of unit, a person learns both, and each piece of content has to be placed in one of
  them.
- A station's later improvements reach a repository only when someone applies it again and reads the diff
  ([0013](0013-organization-content.md)), while an ingredient's reach it with a move of its pin.
- A repository that keeps its own pin of an ingredient runs a station's entries against an ingredient version the
  station did not list; the difference is reported, not prevented.
- When two stations propose different values for one entry, the one applied first stays.
- The list of applied stations records what was applied, not what the repository still holds.

Must be maintained:

- The ingredient and station schemas ([0005](0005-schema-compatibility.md)), and the validation that refuses entries or
  templates in an ingredient and renders or floor rules in a station.
- The application rules, shared with [0013](0013-organization-content.md), and the report of every difference they
  leave, a kept recipe included.

## Confirmation

- Validation refuses a fixture ingredient that holds entries or templates, and a fixture station that holds renders or
  floor rules.
- Applying a fixture station pins every ingredient it lists at the content hash it lists, and writes its entries and
  templates, in one diff.
- In a fixture that already pins one of those ingredients at another content hash, applying the station keeps the
  fixture's pin and reports the difference.
- Removing a station from a fixture's list of applied stations changes no rendered byte, and unpinning an ingredient
  removes exactly its renders ([0007](0007-ownership.md)).
- A fixture whose data names one ingredient twice, or one applied station twice, fails validation.
- Applying two fixture stations that set one entry to different values keeps the first station's value and reports the
  second's.
- Applying a fixture station that pins a recipe copies the recipe, with its origin, in the same diff as the station's
  entries; in a fixture that already holds a recipe of that name, the recipe stays byte-identical and is reported.
- Applying a fixture station whose recipe archive differs from the content hash the station gives is refused, and the
  fixture stays byte-identical.
