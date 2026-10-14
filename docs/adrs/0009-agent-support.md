# 0009. Ingredients model each agent's native configuration

## Objective

**Problem.** Coding agents use different configuration formats. Maintaining the same repository setup by hand for
several agents duplicates work, and a change made for one can be missed for another.

**Goal.** Teams can reuse configuration choices across agents while seeing what each agent will actually receive.

## Context

- The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) assigns native program files and
  configuration to [ingredients](../features/glossary.md), held and shipped with trusted Groovy recipes by cookbook
  repositories.
- Worked example: `acme/api` configures two agents to permit its test command. A teammate changes the command in one
  native file and forgets the other. Shared recipe inputs can carry that choice to both program models.
- A permission rule, hook setting, instruction file or skill can have a different representation in each program.
  Some settings have no equivalent in another program. Writing configuration does not establish equivalent runtime
  behavior or make Cook the program's runner.
- Existing native files can contain settings outside the requested change. The preservation reasoning in
  [0008](0008-native-configuration.md) applies to agent files too.

## Decision

1. Each agent's native files and configuration have their own ingredient, as for any program
   ([0022](0022-tool-integration.md)), maintained and distributed by cookbooks independently of the engine.
2. Recipes can reuse choices across program models, but their declared native output is the subject of review.
   Agent-specific settings remain expressible through the relevant native model.
3. Agent configuration follows the ordered composition of [0007](0007-ownership.md) and the preservation rules of
   [0008](0008-native-configuration.md).
4. Each agent enforces the permission and network settings a recipe declares for it; Cook writes those settings as
   declared batch operations, within the trust limits of [0011](0011-safety-invariants.md).

## Reasons

- **1.** Program formats change independently. Keeping their knowledge in cookbook models lets authors update agent
  support without turning each agent's settings into engine responsibilities. Cookbook authors must maintain that
  program knowledge.
- **2.** Shared choices reduce the duplicate editing in the example, but translation can lose meaning. Reviewing
  native output exposes what each program receives without promising an equivalence the file changes cannot prove.
- **3.** Agent settings share files with existing content. Targeted operations preserve that content, while ordered
  overlap permits deliberate overrides. A reviewer must inspect the final result rather than assume an earlier value
  survives composition.
- **4.** Running and supervising an agent belong to the agent and its users ([0001](0001-role-of-the-tool.md)).
  Configuring another program's controls cannot make trusted executable recipes safe to run; recipe trust and
  approval of declared output are separate decisions.

## Rejected alternatives

### Maintain every agent's configuration independently by hand

At its best, each program's full native format is directly available. It loses reusable configuration choices and
requires the repeated edits illustrated in Context. Native-specific choices remain available in program models.

### Require one agent-neutral policy and launch contract

At its best, one declaration, written once for every agent, promises the same behavior everywhere. It loses because
it requires translation and supervision contracts beyond the file-operation engine, and hides differences when a
program lacks an equivalent setting.

## Consequences

Costs:

- Shared inputs can produce different native output and behavior across programs; reviewers must assess those
  differences.
- Cookbook authors must keep agent models compatible with the native formats they describe.

Must be maintained:

- Program-specific configuration knowledge in cookbooks and reviewable native-file operations in the engine.

## Confirmation

- In an example configuring two agents from shared recipe inputs, does the combined diff show each agent's native
  output, with any program-specific choices explicit?
- With overlapping agent-setting operations, does the later declared value appear in the final diff while unrelated
  settings remain intact?
- Does agent-support documentation describe Cook as writing native settings, and leave starting, supervising and
  enforcing permissions to the agent?
