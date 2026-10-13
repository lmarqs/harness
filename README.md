# the-cook

<p align="center"><img src="docs/assets/the-cook.png" alt="the-cook: pixel-art portrait of a man in a black pork-pie hat lowering dark sunglasses" width="640"></p>

**The cook: cook the best product without any source of contamination.**

## Objective

**Problem.** Teams share repositories, and each person works with their own programs: coding agents, loop runners,
trackers and editors. Those programs leave their files in the repository. One person's setup gets committed, and every
teammate inherits it. A way of working learned in one repository reaches the others only by copying it by hand, and the
copies drift.

**Goal.** A way of working learned once reaches every repository through one reviewed change, and is withdrawn the same
way. Everyone keeps their own way of working, and when they are done only the work remains. People who do not use
the tool never notice it.

## Current implementation

The core and CLI use Kotlin, with Groovy document and filesystem adapters. Cookbook suppliers and repository recipes use Groovy. Ingredients publish their own builds and versions; recipes compose their contributions into a batch.

`./cook <recipe>` renders the desired batch into native project files. Ingredients can share separate blocks or keys in a file, while whole files and symlinks have exclusive owners. Cooking checks for managed edits and applies changes atomically. Every batch is the complete desired setup.

```sh
./gradlew check cliBuild cookbookBuild
./cook init
./cook cookbook --policy=.agents/policy.yaml --dry-run
./cook cookbook --policy=.agents/policy.yaml
```

The agent setup recipe requires a policy file and shared skills; see its [README](recipes/cookbook/README.md). See [the core implementation](core/README.md) for ownership, contribution identities, migration and differences from the earlier design documents.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| The design: the parts, the commands, the requirements and the decisions behind them | [Architecture](docs/architecture.md) |

## Install

There is no published release. Build the CLI from source with Java 17 and `./gradlew cliBuild`, then use the repository's `./cook` entry point.
