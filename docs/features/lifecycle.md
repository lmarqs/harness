# Workflow configuration and engine boundaries

## Objective

**Problem.** Teams want to share how they work without confusing configuration of their programs with control of the
whole development process.

**Goal.** Cook makes reusable setup reviewable while each configured program and the team retain responsibility for
running the workflow.

## Configuration and execution

[Cook's architecture](../architecture.md) defines a file-operation engine. Trusted Groovy recipes produce an ordered
batch of file operations; Cook shows the final terminal diff, obtains whole-batch approval, saves recovery information
before target writes, and performs the writes. See [apply](render.md) and [revert](remove.md).

Cookbooks supply ingredients that model programs' native files and configuration, and recipes that use those models.
A recipe can describe agent settings, shared instructions, task-runner configuration, or CI files. Programs consume
the resulting files directly. Launching a configured agent or loop runner, scheduling its tasks, judging completion,
and delivering its work belong to that program and the team.

Planning, implementation, checks, review, and publication remain the team's workflow. External commands are not batch
actions. Approval, destination boundaries, and rollback cover declared file operations; trusted recipe execution is
not sandboxed, and its arbitrary side effects are outside those guarantees.

## Related responsibilities

[Repository checks](verify.md) and [task-tracker configuration](tasks.md) can be described in native files. The CLI
is the public cooking interface; Gradle builds and packages Cook and cookbooks.
Ingredient/recipe APIs and hooks require their own discussion.
