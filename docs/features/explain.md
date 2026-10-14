# Finding the contract and its reasoning

## Objective

**Problem.** A contributor with a question about Cook's behavior needs to know which document answers it and which
one is authoritative.

**Goal.** Each question leads to one account of Cook's responsibilities and the reasons behind its operation rules.
The [feature index](README.md) lists pages by topic; this page routes by question and states which source wins.

## Reading the references

[Cook architecture](../architecture.md) states the intended design. Supporting feature pages follow it, and
[ADRs](../adrs/README.md) explain the reasoning behind that design. The [glossary](glossary.md) defines its terms.

| Question | Read |
| --- | --- |
| What does the engine do, and what stays outside it? | [Architecture](../architecture.md) |
| How are recipe operands, arguments and approval scoped? | [CLI reference](cli.md) |
| How do ordered file operations, ownership, source paths and the batch record work? | [Apply](apply.md) |
| How is a recorded batch reversed? | [Revert](revert.md) |
| What does approval protect, and what requires recipe trust? | [Safety guidelines](safety.md) |
| How can recipes share native instructions and reviewed knowledge? | [Repository guidance](context.md) and [memory](memory.md) |
| What evidence helps diagnose an unexpected result? | [Configuration diagnostics](doctor.md) |
| How is Cook packaged and versioned? | [Installation](installation.md) and [wrapper](wrapper.md) |

The [root README](../../README.md), [core implementation guide](../../core/README.md) and recipe READMEs
describe current execution. Read them for what runs today. When investigating a particular setup, keep the engine
version distinct from the supplied recipe version and consult the cookbook documentation for the program
configuration it models.

Prefer links to maintained explanations over copying a command catalog or broad rules into every instruction file.
Keep repository-specific guidance concise and describe the sources and program prerequisites needed to use it.
