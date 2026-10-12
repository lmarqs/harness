# 0028. The tool helps people work together without controlling how anyone works

## Objective

**Problem.** People who change one repository each work their own way: their own editor, their own coding agents and
the settings and extensions they keep for them, their own loop runner and task list, or none of these. A tool that tries
to make them all work one way gets in the way of everyone whose way differs, and still stops nobody, because a person
can always start a program directly, keep settings of their own or write a wrapper of their own. A tool that claims a
control it does not have misleads the people who rely on its word.

**Goal.** Adopting the tool takes no way of working from anyone, and every guarantee the tool states holds for what the
tool itself does.

## Context

- A coding agent is a program a person can start directly, with arguments, settings and extensions of their own.
  Claude Code 2.1.296 combines a person's own settings, kept in their home directory and in a local override file in
  the project, with the project's settings. Its headless runs load the same context as an interactive session,
  including the person's own plugins, hooks and instructions, unless it is started in its bare mode
  ([headless runs](https://code.claude.com/docs/en/headless.md)). Codex 0.160.1 also reads the person's own rules and
  configuration from their home directory.
- A loop runner starts whatever command its configuration names for each task, and a person can write a wrapper of
  their own around any program.
- Changes made with different editors, extensions, plugins, skills, loop runners and task lists land in the same
  repository and reach it through the same review, branch protection and CI.
- Example: at acme, one developer works in VS Code with a methodology plugin in their coding agent. Another works in
  IntelliJ and runs a loop runner over a task list kept in their own clone. A third uses no coding agent at all. All
  three change `acme/api`, and every change of theirs reaches the default branch through the same review and CI.

## Decision

1. The tool helps: it sets up what a repository shares, runs the steps a person or a worker asks of it, and reports
   what it did. Nobody has to work through it, and it neither detects nor prevents work done any other way.
2. Each person's own choices stay theirs: their editor, the settings and extensions they keep for a worker program, the
   skills and plugins installed for them, and the loop runner and task list they use. The tool never requires one, never
   writes or checks them, and reads them only when that person asks it to, as when they import their own permission
   rules into the repository's policy ([0016](0016-permission-adoption.md)). They apply in every session that person
   runs, the sessions the tool starts for them included, except inside a jail a repository declares for a worker,
   which lets through only what its options name ([0027](0027-worker-jails.md)).
3. Every guarantee the tool states is about what the tool itself does: what it renders, how it starts the programs it
   runs, what it refuses to do and what it reports. No document claims a limit on a person, or a limit on a program
   beyond what the tool itself sets up for it.
4. Working around the tool is a supported use. The tool has no mechanism that detects or blocks it, and a feature
   whose only purpose is to stop people working around the tool is not built.

## Reasons

- **1, 4.** Any control the tool added would stop only the people who go through the tool, while every way around it
  stays open (Context). It would cost the people it slows down, protect nothing from those who step around it, and call
  for another control after each new way around. Helping instead gives every person a reason to go through the tool and
  costs nothing to those who do not ([0010](0010-dependence-on-the-binary.md)). The trade: the tool cannot report that
  every change in a repository went through it. Rules that must hold for every change stay where every change passes:
  review, branch protection and CI.
- **2.** The people in Context differ exactly in these choices. A tool that required, rewrote or stripped them would
  take a way of working from someone who never chose to give it up. Applying a person's own configuration in the
  sessions the tool starts for them keeps those sessions behaving as the person's other sessions do. Reading them only
  on that person's request keeps a personal choice from becoming a shared one by accident. A jail is the exception
  because a repository declares it to keep a worker away from the person's home, with its keys and tokens, and the
  person's own configuration lives there ([0027](0027-worker-jails.md)). The trade: the same task can run differently
  on two machines. What stays the same is what the tool renders and the checks it replays
  ([0011](0011-safety-invariants.md)).
- **3.** A guarantee about what others cannot do turns false the moment one of them starts a program directly
  (Context), and a reader who trusts it leaves a gap open without knowing. A guarantee about the tool's own actions
  holds whoever else acts. The trade: every guarantee carries its scope, and the documentation says what lies outside
  it.

## Rejected alternatives

### A tool that makes everyone work one way

The tool detects or blocks work done outside it, and requires every person to install and use the programs the
repository names. At its best, every change in every repository provably passes the same stages, and a reviewer never
has to ask how a change was made. It loses because a person can always start a program directly (Context), so the
proof is never complete; its restrictions fall only on the people who did use the tool; and every new way around it
calls for another restriction.

### Runs that load only the repository's configuration

The tool starts each worker it runs for a task with the person's own settings, extensions and instructions left out, so
a task behaves the same on every machine. At its best, two people running the same task get the same behaviour, and
nothing outside the repository shapes the result. It loses because the person's own configuration is a way of working
they chose (point 2), leaving it out overrides that choice in the one session the person is not watching, and the
outcome is already judged by checks the tool replays from the repository's committed rules.

## Consequences

Costs:

- The tool cannot report that every change in a repository went through it.
- The same unattended task can behave differently on two machines, because each person's own configuration applies.
- A team that wants a rule to hold for every change keeps it in review, branch protection or CI, where every change
  passes.

Must be maintained:

- Every statement of a guarantee, in every document, names the tool's own action and its scope.
- Features stay optional and additive: each helps those who use it and costs nothing to those who do not
  ([0010](0010-dependence-on-the-binary.md)).

## Confirmation

- Review rejects a document that states a limit on a person, or a limit on a program beyond what the tool sets up for
  it, instead of what the tool does.
- Review rejects a feature whose purpose is to detect or block work done outside the tool.
- A fixture starts a worker with a stand-in home directory that holds the person's own settings, and asserts that the
  tool neither reads nor changes them.
