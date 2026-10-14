# 0028. Cook supports shared setup without controlling how people work

## Objective

**Problem.** People who change one repository each work their own way: their own editor, coding agents, settings,
plugins, loop runner, and task list, or none of these. A tool that requires everyone to work one way gets in the way
of those whose choices differ. A tool that claims control over programs people can start directly misleads those
who rely on its guarantees.

**Goal.** Using the tool takes no way of working from anyone, and each guarantee names the actions it covers.

## Context

- People can start their programs directly or through their own wrappers, independently of Cook.
- Worked example: one acme developer uses VS Code and an agent plugin, another uses IntelliJ and a loop runner,
  and a third uses no coding agent. All contribute to `acme/api` through the team's review and CI conventions.
- The agreed [architecture](../architecture.md#scope-and-responsibilities) defines a file-operation engine that
  configures native program files.
- Recipes are trusted executable Groovy code. Their declared batch operations are only part of what that code
  can do with the user's privileges.

## Decision

1. Cook helps people share configuration through requested file changes. Nobody has to work through it, and it
   neither detects nor prevents work done another way.
2. People choose their editors, agents, plugins, loop runners, and task lists. Cook can configure their native
   files through declared recipe operations, without requiring teammates to use those programs or making their
   execution an engine responsibility. Personal setup backup and session orchestration remain future capabilities.
3. Guarantees about review, approval, output boundaries, and recovery apply to Cook's declared batch operations
   ([0012](0012-write-boundary.md)). They do not constrain people, programs started independently, or arbitrary side
   effects in trusted recipe code. Cook does not sandbox recipe evaluation.
4. Rules a team needs for every contribution belong in its review, branch protection, and CI conventions.
   Configuring those systems through file operations does not make Cook the authority that enforces them.

## Reasons

- **1.** Control over only those who use Cook would leave every independent way of working open while slowing down
  willing users. Cook shares reviewed configuration without trying to police its use.
  The trade is that Cook cannot report that every change in a repository went through it.
- **2.** The developers in Context differ in their program choices. Native configuration lets each use the setup
  they need without placing all their sessions under Cook. The trade is that configuring a program does not guarantee
  its behavior or make sessions identical across machines.
- **3.** The engine can present and recover the file operations it handles. It cannot make the same promise about
  independent programs or arbitrary recipe code. The trade is that users must trust executable recipes and every
  guarantee must identify its scope.
- **4.** Review and CI conventions apply regardless of which editor or agent produced a contribution. Cook applies
  only when invoked, so treating it as universal enforcement would leave unobserved paths. The trade is that teams
  maintain those controls independently of their recipe setup.

## Rejected alternatives

### Require everyone to work through Cook

At its best, one path gives every contributor the same setup and stages. It loses because programs can be started
independently, so enforcement remains incomplete and restrictions fall on people who chose to use the tool.

### Make Cook supervise every configured program

At its best, one controller could standardize sessions and report their outcomes. It loses because native-file
configuration does not grant that control, and worker supervision and session orchestration are outside this engine's
responsibilities. Their future design is not decided here.

### Treat batch approval as a recipe sandbox

At its best, reviewing a diff would authorize every effect of a recipe's evaluation. It loses because executable recipes
can act before or outside declared batch writes; approval cannot restrict or recover those arbitrary effects.

## Consequences

Costs:

- Cook cannot prove that every repository change went through it or that independently started programs follow its
  configuration.
- Teams keep universal contribution controls in their own review and CI conventions.

Must be maintained:

- Documentation that names the declared operations each guarantee covers and the trusted-code boundary.
- The separation between configuring programs and controlling their execution.

## Confirmation

- Review descriptions of a developer using Cook alongside one who does not: neither must change their editor or agent
  choice to contribute.
- Review every guarantee for a declared batch action and its scope. No recipe-sandbox promise is inferred from native
  configuration or batch approval.
- Review features against the engine's file-operation responsibility.
