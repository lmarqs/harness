# Architecture decision records

An architecture decision record (ADR) states one decision that shapes the structure of this project and, above all,
why it holds. Each ADR records:

- its objective;
- the facts it answers;
- the reasons that connect those facts to the decision;
- the alternatives that lose, and why;
- what the project maintains because of it.

The [index](#index) lists every ADR with the question it answers.

## Authority

[Cook architecture](../architecture.md) states the intended design. ADRs explain decisions and their reasons within
that design; the [features](../features/) pages and [user-experience standards](../ux/) follow it too. When an ADR or
standard conflicts with the architecture, correct the supporting document. An earlier decision does not override
the agreed architecture.

No two ADRs contradict each other, and each rule has one owning ADR. A point that applies another ADR's rule is a
one-line link to that ADR, which keeps the contract and its reasons in one place.

## An ADR states what is true now

An ADR describes the intended decision in the present tense, without status labels or a chain of replacements.
Correct contradictory definitions in place and review affected references. Documentation can record an agreed
decision before implementation catches up; descriptions of current behavior must remain accurate, and implementation
progress belongs in issues. Git history holds the earlier versions.

A decision that stops applying is deleted, together with every reference to it. Its number is not reused.

Reasons: a corpus that accumulates superseded records forces every reader to reconstruct the current state from a
chain of partial replacements, and a reader who stops early acts on a decision that no longer holds. A coding agent
that reads the corpus before making a change is that reader on every run.

## The objective comes first

Every ADR opens with its objective, before any fact or decision, so a reader knows why the subject is on the table and
what it must achieve before reading how it is settled ([philosophy](../philosophy.md#start-from-the-objective)). The
Objective section has two parts:

- **Problem.** Who has the problem, and what goes wrong for them or what they need and lack, in at most four
  sentences.
- **Goal.** The outcome that holds once the problem is solved, stated so that anyone can tell whether it holds, in at
  most two sentences.

Neither part names a solution: no mechanism the decision introduces, none of the tool's commands, files or settings.
One ADR has one objective; a decision that serves two objectives is two ADRs. When the problem no longer exists, the
decision no longer applies, and the ADR is deleted.

Reasons: a decision read without its problem is judged against whatever problem the reader assumes. A decision without
its goal cannot be confirmed, because nothing says what success looks like. A decision whose problem is gone stays
in force only because nobody asked why it was there.

## Reasons come first

A decision without its reasons cannot be applied to a case it did not foresee, and cannot be changed safely, because
nobody can tell which facts it depends on. So every ADR justifies every point it decides:

- Each numbered point of the Decision serves the Objective and has at least one reason in Reasons.
- A reason ties a fact from Context, or another ADR, to the point, and states what the decision trades away.
- Context holds the facts and examples that make the objective concrete, and a reader with only this repository and
  public sources can check or follow every one of them: a third-party tool's documented behavior, with the version it
  was checked against; a property of the tool or of the problem itself; or a worked example, named as one, set in a
  made-up organization (`acme`). It holds no conclusions. The objective is understood without it; Context is where a
  reader checks it.
- Context never relies on what the reader cannot see: no counts from a private survey, no cases from repositories,
  people or conversations outside this project. Why: the ADRs are read by people who were not there, and a fact they
  cannot check is only an assertion, while an example they can follow makes the same point and stays true.
- Each rejected alternative is stated at its best before the reason it loses.

An ADR whose Reasons section does not cover every point is incomplete, whatever its Decision says.

## When to write one

Write an ADR for a decision that is hard to reverse or that constrains how other parts are built: the implementation
language, the contract between the tool and its destination, the boundaries on declared writes, how the batch record
is kept, how releases are trusted.

Do not write an ADR for a feature, a command, a flag, a file field, a task or a plan. Those belong in the
[features](../features/) pages and the [user-experience standards](../ux/). If a proposed ADR reads as a description of
something the tool does, it is not an ADR.

One ADR holds one decision. If its points serve two decisions, it is two ADRs.

## Naming

- **File:** `NNNN-<topic>.md`. The number is a permanent identifier, never reused or reassigned; a number missing from
  the index belongs to a deleted decision. The topic names the question the ADR answers, in kebab-case nouns
  (`implementation-language`, `release-trust`), so the file name stays valid when the answer changes and links never
  break.
- **Title:** `# NNNN. <decision>`: one present-tense declarative sentence that states the answer, at most 12 words,
  with no semicolon, no rationale and no "and" joining two decisions. When the decision changes, the title changes
  with it; the file name does not.
- **Links:** other ADRs are linked by file name, `[0011](0011-safety-invariants.md)`.

## Writing rules

- Present tense, no hedges.
- Prefer generic mechanism names: the wrapper, the batch, the batch record, the destination, an ingredient,
  a recipe. Use concrete command, flag, setting, environment-variable, exit-code or file references when necessary
  to explain a decision, its boundary or its evidence, such as `--batch-approve` or the batch record under `.cook/`.
  Keep detailed usage in the [features](../features/) pages and terminology in the
  [glossary](../features/glossary.md); a concrete reference is not a reason to add an undecided contract.
- Link the [glossary](../features/glossary.md) at the first use of an engine term in each ADR.
- A third-party product whose behavior a decision depends on is named with the version that behavior was checked
  against. Prior art and rejected tools are named without a version, because nothing depends on their behavior.
  Build tooling (the JDK, jpackage, Kotlin, Groovy, Gradle) is named without a version, because the build files pin
  those versions.
- Organizations appear only as the makers of third-party products whose behavior matters; examples use `acme`.

## Template

```markdown
# NNNN. <The decision, one present-tense sentence>

## Objective

**Problem.** Who has the problem, and what goes wrong for them or what they need and lack. No solution.

**Goal.** The outcome that holds once the problem is solved, stated so anyone can tell whether it holds.

## Context

The data and examples that make the objective concrete, each with its source. No conclusions.

## Decision

1. <A point of the decision, present tense.>
2. ...

## Reasons

- **1.** <Why point 1 holds: the fact it answers, what it gains, what it trades.>
- **2, 3.** <One reason may justify several points.>

## Rejected alternatives

### <Alternative>

The alternative at its best, then the reason it loses.

## Consequences

Costs:

- <What becomes harder, or a risk the decision accepts.>

Must be maintained:

- <What the project keeps up because of this decision.>

## Confirmation

- <A concrete review question, or an existing test, CI job or measurement, that shows compliance and that the goal
  holds.>
```

## Index

| ADR | Question | Decision |
| --- | --- | --- |
| [0001](0001-role-of-the-tool.md) | What is the tool's role? | Cook reviews and performs recipe-declared file operations |
| [0002](0002-division-of-work.md) | How are recipe choices separated from batch execution? | Cook separates recipe choices from batch execution |
| [0003](0003-implementation-language.md) | In what language, and as what artifact? | Cook retains its JVM stack |
| [0004](0004-runtime-dependencies.md) | How is the runtime supplied? | Cook packages its Java runtime |
| [0006](0006-repository-contract.md) | What does a repository hold? | Repositories hold trusted recipes separately from the shared engine |
| [0007](0007-ownership.md) | What defines the scope of a file change? | Declared targets define changes reviewed against current content |
| [0008](0008-native-configuration.md) | How does Cook edit shared native files? | Declared native-file targets preserve unrelated content |
| [0009](0009-agent-support.md) | How are several agents configured? | Ingredients model each agent's native configuration |
| [0010](0010-dependence-on-the-binary.md) | What depends on the binary being installed? | Native configuration remains usable without Cook |
| [0011](0011-safety-invariants.md) | What do Cook's guarantees cover? | Cook's guarantees cover declared batch operations |
| [0012](0012-write-boundary.md) | What may Cook write? | Cook confines declared batch operations to reviewed destination targets |
| [0013](0013-organization-content.md) | Where do shared organization choices belong? | Cookbooks carry organization choices separately from the engine |
| [0014](0014-directory-layout.md) | Where does each kind of file live? | Native output and local batch records have separate homes |
| [0016](0016-permission-adoption.md) | How are existing permission changes reviewed? | Existing permission changes receive explicit native-file review |
| [0017](0017-engine-version.md) | Which release runs in a repository? | Each repository pins its engine release through a committed wrapper |
| [0018](0018-conventions.md) | How do conventions remain readable without Cook? | Native instruction text states conventions independently of Cook |
| [0019](0019-action-kinds.md) | What does a batch contain? | Batches contain declared file operations |
| [0020](0020-standing-context.md) | How does Cook configure reusable guidance? | Guidance is native program content |
| [0021](0021-release-trust.md) | What does release verification prove? | Reviewed engine pins verify package integrity without proving publisher identity |
| [0022](0022-tool-integration.md) | How is program configuration knowledge supplied? | Ingredients model programs' native configuration |
| [0023](0023-core-and-contracts.md) | How are operation rules separated from external mechanisms? | The core separates operation rules from external mechanisms |
| [0024](0024-ingredients-and-stations.md) | What separates program knowledge from reusable setup choices? | Program models remain distinct from reusable setup choices |
| [0025](0025-recipes.md) | How are setup choices reused? | Trusted Groovy recipes describe reusable file changes |
| [0026](0026-cookbook-trust.md) | What does batch approval establish about cookbook trust? | Cookbook execution requires trust beyond batch approval |
| [0028](0028-place-of-the-tool.md) | What place does the tool take among the people who use it? | Cook supports shared setup without controlling how people work |
| [0029](0029-cookbook-distribution.md) | How are cookbooks released and resolved? | Cookbooks release by git tag, with jars as optional packaging |
