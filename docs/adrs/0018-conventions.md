# 0018. Conventions the tool establishes are written to outlive it

## Objective

**Problem.** Teams can stop using the tool at any time, and many teammates never use it. The working agreements it sets
up, such as where working copies live and which branch work starts from, must stay readable and true without the tool,
without filling every agent session's context with text about the tool.

**Goal.** A repository's conventions read correctly and hold whether or not anyone runs the tool, at a small, fixed cost
to every agent session.

## Context

- Agents read instruction files, not the tool's configuration. Claude Code 2.1.x reads `CLAUDE.md` and the files it
  imports; Codex CLI 0.16x reads `AGENTS.md`.
- People, agents and third-party tools use the tool's clone-local state directly: they work inside the working copies
  the tool creates, and a loop runner, a program that runs agents unattended one task after another, runs inside them
  ([0014](0014-directory-layout.md)). Where these live and which branch work starts from are conventions all of them
  rely on.
- Not every teammate installs the tool ([0010](0010-dependence-on-the-binary.md)); everyone shares the committed files.
- Working copies can hold uncommitted work, and cleanup acts only on what the binary recorded creating
  ([0012](0012-write-boundary.md)), so removing the tool leaves clone-local state in place ([0007](0007-ownership.md)).
  State whose conventions and ignore entries are gone is unexplained, and git lists it as untracked.
- Some instructions hold with or without the tool: where working copies are, which branch work starts from, which
  permissions are in effect. Others are true only while the tool is present: which of its commands checks work, how to
  trigger delivery.
- Instruction files are read at the start of every session; every line costs context in every session of every agent.
- In `.gitignore`, a path stays ignored while any entry covers it; removing an entry un-ignores what only that entry
  covered.
- Managed blocks are tool output ([0007](0007-ownership.md)), rendered from templates and the repository's data
  ([0002](0002-division-of-work.md)); they are not the prose, memory or domain skills the binary never writes
  ([0011](0011-safety-invariants.md)).

## Decision

1. Every convention the tool establishes is stated in plain language in a managed block of `AGENTS.md`, and every path
   it establishes that the repository does not track is listed in a managed block of `.gitignore`
   ([0014](0014-directory-layout.md)).
2. Managed blocks are of two classes. Convention blocks state facts that stay true without the tool and never name its
   commands. Tool blocks describe how to use the tool and may name its commands.
3. Eject keeps convention blocks as plain text without markers and removes tool blocks ([0007](0007-ownership.md)).
4. Each block belongs to the capability that establishes its convention, and removing the capability removes its
   blocks ([0010](0010-dependence-on-the-binary.md)). Each capability writes its own ignore entries even when another
   capability's entry already covers them.
5. The tool's content in instruction files is bounded; detail lives in the shipped documentation
   ([0002](0002-division-of-work.md)). The health check reports a repository whose blocks exceed the bound.
6. Block text states conventions as facts; only the markers identify it as managed.

## Reasons

- **1.** Agents act on what instruction files say, not on what the tool's configuration holds, so a convention that is
  only in the configuration is invisible to them. `AGENTS.md` is the file both supported agents reach: Codex directly,
  Claude Code through an import line the tool keeps in `CLAUDE.md`, itself a convention block. Writing every untracked
  path into `.gitignore` keeps clone-local state untracked in every clone, including clones without the tool; a path the
  repository chose to track stays tracked. People and third-party tools working without the tool read the same text, and
  after a removal the remaining state is still explained.
- **2, 3.** A sentence that names one of the tool's commands becomes false the moment the tool is gone, and an agent
  that reads it runs a command that is not there. Splitting blocks by whether their text holds without the tool lets
  eject keep everything that stays true and remove everything that would mislead. The trade is that every block's class
  is a design decision that must be made and kept.
- **4.** Capabilities are adopted and removed independently ([0010](0010-dependence-on-the-binary.md)), so their text
  must be too. With shared ignore entries, removing one capability would un-ignore paths another capability still
  creates, and git would start listing that state as untracked. Duplicate entries cost one line each and make removal
  local.
- **5.** Every line in an instruction file is read in every session, so the tool's share of that file is a recurring
  cost multiplied by every session of every agent. Bounding it keeps the cost fixed, while the full rules are read on
  demand from the shipped documentation. A report instead of a refusal leaves the choice with a repository that accepts
  a larger block, for example one that declares many standing-context entries.
- **6.** A reader needs the convention, not a note about the tool. Text that does not describe itself as generated
  reads correctly after eject strips the markers.

## Rejected alternatives

### Conventions only in the tool's configuration

The configuration is the one place the conventions are stated, and the tool's documentation explains them. At its best,
nothing is stated twice and no instruction file grows. It loses because agents read instruction files, not the tool's
configuration. People and third-party tools working without the tool find no statement of its conventions, and after
removal the remaining state follows rules nobody wrote down.

### One block holding everything the tool writes

At its best, there is one pair of markers per file and one place to read the tool's text. It loses because eject cannot
keep part of a block, so it would keep text that names missing commands or drop conventions that still hold. Removing
one capability would also rewrite text that belongs to the others.

### Conventions in a separate file linked from the instruction file

At its best, the instruction file stays short and the conventions sit in one file of their own. It loses because Codex
CLI 0.16x reads `AGENTS.md` itself and an agent follows a link only when it judges the link relevant, so whether an
agent learns a convention becomes a matter of chance.

## Consequences

Costs:

- Each convention is stated twice, in the repository's data and in the instruction file; the drift check keeps the two
  equal ([0007](0007-ownership.md)).
- Managed blocks lengthen the instruction file that agents read at the start of every session.
- After an eject, convention blocks and native configuration have no source to render from; later changes are hand
  edits.
- After an undo, clone-local state remains without its conventions or ignore entries until someone deletes it.
- `.gitignore` carries entries that overlap.

Must be maintained:

- For each capability, the conventions it establishes, the blocks that state them, and each block's class. Block ids
  and classes are part of the public contract ([0006](0006-repository-contract.md), [0007](0007-ownership.md)).

## Confirmation

- For each capability, a fixture asserts that every convention it establishes appears as a block in `AGENTS.md` and
  every path it establishes in `.gitignore`; disabling the capability removes those blocks.
- A scan of every rendered convention block in the fixture suite finds none of the tool's commands.
- Fixture blocks contain no statement, outside their markers, that they are managed or generated.
- After an eject, every convention block's text and every ignore entry remain without markers, and no tool block
  remains.
- After removing one capability from a fixture, every path the remaining capabilities create is still ignored by git.
- The health check reports a fixture whose blocks exceed the bound.
