# 0002. Cook separates recipe choices from batch execution

## Objective

**Problem.** Sharing setup mixes mechanical file editing with choices about a project's prose, permissions and way of
working. Hand copying loses consistency, while guessing a team's intent can silently rewrite configuration it wanted
to keep.

**Goal.** Developers can review the concrete effect of shared setup while retaining control over its content and choices.

## Context

- The [architecture](../architecture.md#evaluation-review-and-writes) defines trusted recipe evaluation followed by
  ordered file operations, one combined diff, approval and recovery.
- Recipe inputs include supplied sources, arguments and current target content. Executable Groovy can also consult
  changing external inputs; identical recipe source bytes alone do not establish identical results.
- Worked example: acme uses one recipe to set an agent option and another to override it. The requested order matters,
  and a developer may have edited the current value before the next invocation.
- Prose, skills and policy can be declared file content. Their meaning comes from recipe authors and users; writing
  those bytes does not require Cook to infer their intent.

## Decision

1. People and recipe authors choose the content and configuration they want. Cook evaluates their trusted recipes and
   mechanically composes the declared operations against current content in the supplied order.
2. Cook shows the final combined diff and obtains whole-batch approval before target writes. Existing or edited content
   uses that same flow; recorded ownership never blocks a change ([0007](0007-ownership.md)).
3. Every apply invocation evaluates the supplied recipes again. History does not mark recipes as already
   applied or skip evaluation. An unchanged result may produce an empty diff; arbitrary executable recipes need not
   produce an unchanged result on repeated invocation.
4. Cook can write declared prose, skills and policy like other file content. Neither deterministic batch handling nor
   review implies a sandbox for recipe evaluation.

## Reasons

- **1.** Mechanical composition gives the acme example a defined result: later instructions replace earlier values at
  an overlapping target. Authors retain choices that depend on project intent. The trade: the engine does not decide
  which recipe or configuration is appropriate.
- **2.** The actual diff exposes changes to current content, including manual edits, without refusing a change
  because someone else last wrote that content. The trade: reviewers must assess the proposed changes; approval is for
  the whole batch.
- **3.** Current content and recipe inputs can change between invocations. Re-evaluation respects the request instead
  of treating history as an execution cache. The trade: recipe authors who want reproducible results must control the
  inputs their code uses; Cook cannot promise universal byte-for-byte determinism or idempotence.
- **4.** Writing supplied text is mechanical even when choosing that text required judgement. Banning entire file
  categories would prevent useful program configuration without resolving trust. The trade: declared file changes are
  reviewable, but arbitrary recipe side effects are outside the diff and recovery guarantees.

## Rejected alternatives

### Universal determinism enforced by data-only inputs

At its best, a closed declarative model makes outputs reproducible for fixed inputs. It loses because the chosen
architecture evaluates trusted Groovy recipes, and current content and external inputs may change. It cannot justify
skipping an invocation as already applied.

### Setup entirely by hand or by a coding agent

At its best, every repository gets tailored choices with no configuration engine to maintain. It loses the common
path for ordered composition, combined file review and recorded recovery. People or agents can author content while
Cook handles the declared file operations.

## Consequences

Recipe authors maintain the choices and inputs their recipes use. Cook maintains operation order and the separation
between evaluation, review and writes.

## Confirmation

Review a composed batch against the [architecture](../architecture.md#ordered-composition-and-declared-targets):
later declarations determine the final preview, unrelated content is preserved, and a repeated invocation evaluates
current inputs. Review prose and policy operations under the same approval contract as other declared file content.
