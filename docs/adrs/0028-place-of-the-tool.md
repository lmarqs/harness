# 0028. Cook supports shared setup without controlling how people work

## Objective

**Problem.** People who change one repository each work their own way: their own editor, coding agents, settings,
plugins, loop runner, and task list, or none of these. A tool that requires everyone to work one way gets in the way
of those whose choices differ. A tool that claims control over programs people can start directly misleads those
who rely on its guarantees.

**Goal.** Nobody has to change how they work to use the tool, and each guarantee names the operations it covers.

## Context

- People can start their programs directly or through their own wrappers, independently of Cook.
- Worked example: one acme developer uses VS Code and an agent plugin, another uses IntelliJ and a loop runner,
  and a third uses no coding agent. All contribute to `acme/api` through the team's review and CI conventions.
- The agreed [architecture](../architecture.md#scope-and-responsibilities) defines a file-operation engine that
  configures native program files.
- [Recipes](../features/glossary.md) are trusted executable Groovy code. Their declared batch operations are only part
  of what that code can do with the user's privileges.

## Decision

1. Cook helps people share configuration through requested file changes ([0001](0001-role-of-the-tool.md)). Nobody
   has to work through it, and it neither detects nor prevents work done another way.
2. People choose their editors, agents, plugins, loop runners, and task lists. Cook can configure their native
   files through declared recipe operations, without requiring teammates to use those programs or making their
   execution an engine responsibility.
3. Cook's guarantees cover its declared batch operations ([0011](0011-safety-invariants.md)); they do not constrain
   people or programs started independently.
4. Rules a team needs for every contribution belong in its review, branch protection, and CI conventions.
   Configuring those systems through file operations does not make Cook the authority that enforces them.

## Reasons

- **1.** Cook cannot police people who do not use it, and restricting those who do slows them down. Cook shares
  reviewed configuration without trying to police its use. The trade is that Cook cannot report that every change in
  a repository went through it.
- **2.** The developers in Context differ in their program choices. Native configuration lets each use the setup
  they need without placing all their sessions under Cook. The trade is that configuring a program does not guarantee
  its behavior or make sessions identical across machines.
- **3.** The engine can present and recover the file operations it handles. It cannot make the same promise about
  programs people start themselves. The trade is that every guarantee must identify its scope;
  [0011](0011-safety-invariants.md) gives the reasons for that scope.
- **4.** Review and CI conventions apply regardless of which editor or agent produced a contribution. Cook applies
  only when invoked, so treating it as universal enforcement would leave unobserved paths. The trade is that teams
  maintain those controls independently of their recipe setup.

## Rejected alternatives

### Require everyone to work through Cook

At its best, one path gives every contributor the same setup and stages. It loses because programs can be started
independently, so enforcement remains incomplete and restrictions fall on people who chose to use the tool.

### Make Cook supervise every configured program

At its best, one controller could standardize sessions and report their outcomes. It loses because native-file
configuration does not grant that control, and supervising programs is outside this engine's file-operation
responsibilities ([0019](0019-action-kinds.md)).

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
- Review every guarantee: does it name the declared batch operations it covers?
- Review a proposed feature: does it run or supervise a program? Such a feature is rejected.
