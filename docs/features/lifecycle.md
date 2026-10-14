# Workflow configuration and engine boundaries

## Objective

**Problem.** Teams want to share their workflow (how they plan, implement, check, review and publish work) without
confusing configuration of their programs with control of the whole development process.

**Goal.** Cook makes reusable setup reviewable while each configured program and the team retain responsibility for
running the workflow.

## Configuration and execution

[Cook's architecture](../architecture.md) defines a file-operation engine: Cook reviews, records and writes the
[batch](glossary.md) of file operations that trusted Groovy recipes declare. See [apply](apply.md) and
[revert](revert.md).

Cookbooks supply ingredients that model programs' native files and configuration, and recipes that use those models. A
recipe can describe agent settings, shared instructions, task-runner configuration, or CI files. Programs consume the
resulting files directly. Launching a configured agent or a [loop runner](compatibility.md#loop-runners), scheduling its
tasks, judging completion, and delivering its work belong to that program and the team.

Planning, implementation, checks, review, and publication remain the team's workflow. External commands are not batch
actions, and trusted recipe execution is not sandboxed; see [safety](safety.md).

## Related responsibilities

[Repository checks](verify.md) and [task-tracker configuration](tasks.md) can be described in native files.
