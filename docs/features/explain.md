# Finding the contract and its reasoning

## Objective

A contributor needs one account of Cook's responsibilities and the reasons behind its operation rules. Examples for
an earlier lifecycle engine or a different implementation must not silently define the target contract.

## Reading the references

[Cook architecture](../architecture.md) states the intended design. Supporting feature pages follow it, and
[ADRs](../adrs/README.md) explain its reasoning. This design does not define an `explain` command, an embedded topic
catalog or a guarantee that a shipped skill contains the same text as a binary.

| Question | Read |
| --- | --- |
| What does the engine do, and what stays outside it? | [Architecture](../architecture.md) |
| How are recipe operands, arguments and approval scoped? | [CLI reference](cli.md) |
| How do ordered file operations, source paths and history work? | [Apply](render.md) |
| How does deleting declarations differ from reversing a run? | [Discard and revert](remove.md) |
| What does approval protect, and what requires recipe trust? | [Safety guidelines](safety.md) |
| How can recipes share native instructions and reviewed knowledge? | [Repository guidance](context.md) and [memory](memory.md) |
| What evidence helps diagnose an unexpected result? | [Configuration diagnostics](doctor.md) |
| How is Cook packaged and versioned? | [Installation](installation.md) and [wrapper](wrapper.md) |

The [root README](../../README.md), [core implementation guide](../../core/README.md) and cookbook usage guides
describe current execution. Read them for what runs today; missing implementation does not invalidate the agreed
design. When investigating a particular setup, keep the engine version distinct from the supplied recipe version and
consult the cookbook documentation for the program configuration it models.

Prefer links to maintained explanations over copying a command catalog or broad rules into every instruction file.
Keep repository-specific guidance concise and describe the sources and program prerequisites needed to use it.

## Boundaries

The engine does not explain or enforce fixed lifecycle stages, worker attempts, verification receipts or delivery
guards. Personal setup backup and agent-session orchestration remain future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Ingredient/recipe APIs and hooks remain a separate discussion.
