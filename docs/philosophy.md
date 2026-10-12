# Philosophy

## Objective

**Problem.** A contributor reading one decision, or a coding agent working for one, cannot tell what the project as a
whole is trying to achieve. A proposal can then solve a problem nobody has, or work against a goal the project holds,
without anyone noticing.

**Goal.** In one read, anyone learns which problems `the-cook` exists to solve, what it is trying to achieve and the
tenets every decision follows from, and can check a new proposal against them.

## The problems

Teams share repositories, and each person works their own way, with their own coding agents, loop runners, trackers
and editors. Each of those programs reads and writes files at fixed paths, often inside the repository. Across people
and repositories, that produces four problems.

1. **One person's setup becomes everyone's.** A loop runner's config, a tracker's git hooks or an agent's settings get
   committed, and every teammate who clones inherits them. For example, a loop-runner config committed in `acme/api`
   starts every teammate's coding agent with its permission checks off.
2. **A way of working spreads only by hand.** A rule learned in one repository, such as which tool files never belong
   in a commit, reaches another only when someone copies it. The copies drift, and withdrawing one is another visit to
   each repository.
3. **A person's own setup is retyped everywhere.** The same personal ignore lines, hooks and agent settings are typed
   again in every clone and on every machine.
4. **Sessions leave traces.** A session's worktrees, databases and configs outlive it in the clone, and can reach the
   next commit or the next image built from it.

## The goals

- A teammate works in a shared repository without inheriting anything from another person's way of working.
- A cook starts their own way of working with one request, and when they leave, nothing of it remains but the work.
- A way of working learned once reaches every repository through one reviewed change, and is withdrawn the same way.
- A cook's own setup follows them to every clone and every machine, and what they keep private stays private.
- A teammate who does not use the tool works exactly as before.

## Two axes, and contamination

Every file a program writes has an owner and a lifetime. The owner is the project or one person; the lifetime is
persistent or ephemeral. Each part of the tool holds one cell.

| | Persistent | Ephemeral |
| --- | --- | --- |
| Project | the **kitchen**: the team's committed rules | the team's build outputs and caches |
| Personal | the **kit**: a person's own setup, in their own repository | **cooking**: one session's traces |

**Contamination** is something personal becoming persistent in the project, such as one person's settings committed,
or a session's state outliving the session. The tool exists to keep the cells apart, and the tagline says so: the
cook, the person, cooks the best product without any source of contamination.

## What `the-cook` is

- **The core** is one Go binary with three parts. The **kitchen** is `.cook/` in the repository: the team's rules for
  tool files, rendered into the files programs read, such as `.gitignore`, `.dockerignore` and agent settings. The
  **kit** is the person's own repository, which writes their setup into their home directory. **Cooking** runs one
  recipe as a session in a directory outside the clone, and deletes the session's state when it ends.
- **The wrapper**, `./cook`, is committed in each repository and pins one release, so every machine and pipeline gives
  the same answer for the same commit.
- **Cookbooks** publish groups of rules, ingredients and recipes. A group is a set of ignore rules; an ingredient
  holds the facts about one program, such as the files it reads; a recipe is one way of working, with its parameters,
  scripts and prompts. A repository or a kit copies what it uses, with its origin and sha256.

It is not a coding agent, a loop runner, a tracker or a task runner, and it is not a safety tool. It works with
whichever programs a person or a team uses, through the ingredients and recipes they copy.

## Tenets

Each tenet answers one or more of the problems above.

### Start from the objective

**Problem.** A feature, decision or rule whose purpose is not stated is judged against whatever purpose its reader
assumes. It cannot be changed safely, and it outlives the reason it was made.

**Tenet.** Everything starts from its objective: the problem it solves or the need it meets, and the goal that holds
once it is solved. Every document states its objective first, in a few plain sentences that a reader understands
without any data. The data and examples come after it, to make it concrete and checkable. A thing whose objective
cannot be named is not built, and a thing whose problem goes away is removed.

**Bound by.** [ADR structure](adrs/README.md#the-objective-comes-first).

### Helps, never controls

**Problem.** A tool that makes everyone work one way charges every person for a choice they did not make. It also stops
nobody, because anyone can start a program directly or keep settings of their own.

**Tenet.** `the-cook` does its part and reports truthfully what it did. It refuses no caller and judges nobody, and a
person or agent who ignores the settings they are given is acting on their own. Every guarantee it states is about
what it does itself.

**Bound by.** [ADR 0028](adrs/0028-place-of-the-tool.md).

### Helps achieve safety, and is not a safety tool

**Problem.** A core that enforces one idea of safety either blocks legitimate work or gives a false sense of safety
outside the cases it knows.

**Tenet.** Safety and compliance come from what a team or a cook copies: recipes, ingredients and groups, and some
recipes are safer than others. A recipe that wants a container or a sandbox starts one from its own script. The core
has no safety floor; its fixed rules cover only what it writes itself, such as never copying a credential into a kit.

### A thin core; ways of working live in recipes

**Problem.** A core that holds a development lifecycle has to change whenever a team's way of working changes, and
makes every team work that way.

**Tenet.** The core sets up a session and leaves the repository clean. Stages, gates, hooks, commits and delivery are
a recipe's scripts. The core's only lifecycle is a session's start and end, and the binary knows no stage name and no
event.

### Knows no program

**Problem.** Support in the binary for one program makes every repository depend on a choice its team did not make,
and makes the binary follow every release of that program.

**Tenet.** Program knowledge is data. The binary holds one reader and writer per file format, and each program's facts
live in its ingredient. A new program, or a new release of one, needs a new ingredient and no new release of the tool.

### Copied, never live

**Problem.** A setup inherited live from a shared source changes under a repository that changed nothing.

**Tenet.** Items are copied with their origin and sha256, and copying again is the update, reviewed as a diff. Nothing
is fetched when the tool renders, and trust in a copied item is the team's review of its diff.

### Personal and project never mix

**Problem.** A committed file rendered from one person's setup cannot be rendered again by a teammate or a pipeline,
and the next person's render rewrites it.

**Tenet.** Committed files are rendered only from sources in the repository. A kit is where a recipe can come from,
never where a committed file is rendered from, and no command writes the repository and the home directory in one run.

### Invisible to those who do not use it

**Problem.** Teams stop using tools, and many teammates never start.

**Tenet.** Nothing in an adopted repository needs the binary. The files it derives are committed, so teammates, agents
and pipelines read them without the tool, and removing the tool leaves every line and key as the team's.

### Depends on nothing

**Problem.** Every program a tool needs at runtime is one more thing that must be installed, and at the right version,
on every machine.

**Tenet.** The binary needs only the operating system. It does not depend on git, and it starts no program: whoever
wants git's verdict runs git, and a session hands its process to the recipe's script.
