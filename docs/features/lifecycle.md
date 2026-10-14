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
the resulting files directly. Configuring an agent or loop runner does not make Cook responsible for launching it,
scheduling its tasks, judging completion, or delivering its work.

Planning, implementation, checks, review, and publication remain the team's workflow. The engine does not define fixed
development stages, lifecycle events, worker attempts, verification receipts, delivery guards, or jails. External
commands are not batch actions. Approval, destination boundaries, and rollback cover declared file operations;
trusted recipe execution is not sandboxed, and its arbitrary side effects are outside those guarantees.

## Related responsibilities

[Repository checks](verify.md) and [task-tracker configuration](tasks.md) can be described in native files without
turning either into a Cook execution service. The CLI is the public cooking interface; Gradle builds and packages Cook
and cookbooks rather than providing a second cooking interface.

Personal setup backup (kit) and agent-session orchestration (cooking) remain future capabilities tracked in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Ingredient/recipe APIs and hooks require their own discussion;
this page defines no session protocol or hook design.
