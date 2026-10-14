# 0019. Batches contain declared file operations

## Objective

**Problem.** A reviewer needs to know what approving a configuration change authorizes. Mixing file edits with
service calls or worker launches makes a file diff an incomplete account of the approved work.

**Goal.** A batch's review and recovery cover the declared changes, with clear limits on what approval establishes.

## Context

The [architecture](../architecture.md#evaluation-review-and-writes) and its
[requirements](../architecture.md#requirements) FR-1 through FR-4 define trusted Groovy evaluation followed by review
and execution of declared file operations. External command execution is not a batch action. Recipes are executable code
and can cause side effects outside their declarations.

For example, an acme recipe can write a program's native notification settings. The program later sends the
notification; Cook's batch changes its configuration, rather than sending the notification itself.

## Decision

1. A batch contains file operations, including directory and symlink operations where applicable. Cook does not
   execute external commands, HTTP requests or worker attempts as batch actions.
2. Recipes and their operations compose in declared order against current content. Cook presents the final combined
   diff for whole-batch approval, records recovery information before the first target write, and rolls back batch
   changes on write failure while retaining patches and the failure record.
3. Approval, destination boundaries and rollback govern declared operations. They do not sandbox trusted recipe
   evaluation or cover arbitrary side effects caused by recipe code.
4. Configuring a program through native files does not make Cook that program's runner. Agent-session orchestration
   remains future work in [issue #6](https://github.com/lmarqs/the-cook/issues/6), without an execution design here.
   Recipe hooks and model APIs remain outside this decision.

## Reasons

- **1.** FR-3 gives the batch a scope that a file diff can represent. Native configuration still lets recipes set up
  integrations, but the configured program performs them independently of Cook. The trade is that callers arrange
  any external execution outside the batch.
- **2.** FR-7 and FR-11 through FR-13 make the combined result the unit of review and recovery. Later operations can
  replace earlier values without presenting intermediate results as separate approvals. The trade is that the CLI's
  approval covers the whole batch; choosing individual changes is a separate interface decision.
- **3.** FR-4 separates trusting executable sources from approving their declared writes. A file diff cannot show
  arbitrary code side effects. The trade is that reviewers must trust recipe sources before evaluating them.
- **4.** FR-6 keeps worker supervision and lifecycle events outside the engine. This preserves the distinction
  between configuration and execution without deciding future orchestration or deferred hooks.

## Rejected alternatives

### Retain command, HTTP and attempt action kinds

One declaration system could describe integrations and follow-up work. Its execution effects would exceed the file
batch's preview and recovery contract and restore the retired lifecycle engine's responsibilities.

### Treat batch approval as permission for all recipe effects

One approval would appear to cover everything a recipe does. Evaluation occurs before that approval, and arbitrary
side effects are not recorded file operations. This would promise a guarantee Cook does not provide.

## Consequences

Costs:

- External integrations run through their own programs or callers, outside Cook's batch recovery.
- Trust in executable recipes remains necessary even when every declared write is reviewed.

Must be maintained:

- The separation between declared file operations, trusted evaluation and configured programs' later execution.

## Confirmation

Review against FR-1 through FR-4, FR-6, FR-7 and FR-11 through FR-13. In the acme example, approval covers the native
notification settings diff, not a service request. The record defines no command runner, action schema, worker
launch contract or recipe hook API. These are design review criteria, not claims of implemented behavior.
