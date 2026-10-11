# 0020. Guidance enters worker context through declared deterministic triggers

## Objective

**Problem.** Repositories keep guidance that coding agents should follow, but a coding agent loads it only when the
model judges it relevant. People retype the same invocations at the start of sessions, and guidance someone forgets to
invoke is not applied. Putting all of it in the instruction file instead costs context in every session, including the
many where it does not apply.

**Goal.** Known guidance reaches every session that needs it without anyone invoking it by hand, and costs nothing in
the sessions that do not need it.

## Context

- Repositories keep guidance as skills and directive files: coding guidelines, a SQL review checklist, rules for dates
  and timezones. A coding agent loads a skill when the model judges the skill's description relevant to the request;
  whether a given skill is loaded for a given request is the model's choice.
- People invoke known guidance by name at the start of sessions because description matching does not load it reliably.
  The cost is repeated typing, and guidance that is forgotten is not applied. For example, a developer in `acme/api`
  names the SQL review checklist and the timezone rules at the start of each session, and in the session where they
  forget, the agent writes date handling without the timezone rules.
- Much guidance applies to specific files (SQL rules to migrations) or topics (timezone rules to date handling).
  Instruction files are read at the start of every session ([0018](0018-conventions.md)), so guidance placed there
  costs context in every session, including the many where it does not apply.
- Coding agents expose hook points where a hook can add text to the agent's context. Claude Code 2.1.x has them at
  session start, which it fires again after compacting a conversation, after a tool use, and when the person submits a
  prompt. The set of events differs between worker programs ([0009](0009-agent-support.md)).
- Interactive sessions and attempts need different directives: a rule for how a worker in an attempt reports a blocked
  task means nothing to a person at the terminal.
- A repository's skills are files under `.agents/skills/`, committed like its other files
  ([0014](0014-directory-layout.md)), whether a person wrote them or the team installed them with a tool of its choice
  ([0022](0022-tool-integration.md)).
- Text added to a worker's context acts as instructions. A source that someone can change without review is a channel
  for instructions. A worker in an attempt can write files in its working copy, skills and directive files included
  ([0011](0011-safety-invariants.md)).
- A hook invocation adds at most 50 ms at p95 ([0003](0003-implementation-language.md)), and hooks do nothing on a
  machine where the pinned release is not cached ([0017](0017-engine-version.md)).
- A worker program may run no hook when it runs unattended. Codex CLI 0.160 runs a project's hook only after a person
  has reviewed and trusted it, and in an attempt nobody is there to review anything.
- A worker in an attempt receives its task as a prompt, which the caller passes either on standard input or among the
  worker's arguments. The binary reads a prompt on standard input before the worker starts and passes it on; among the
  arguments, which are the caller's, it cannot tell the prompt from the rest.

## Decision

1. The repository declares guidance entries. Each entry is a skill of the repository or a repository file, with one
   trigger: always (at session start, including when the worker fires session start again after compaction), file paths
   (the first time in a session that the worker reads or edits a matching file), or prompt words (the person's message
   contains one of the listed words or phrases). Each entry applies to the interactive role, the unattended role or
   both, and is delivered either as its content or as a one-line pointer to it. A path or prompt entry is delivered at
   most once per session.
2. Entries are delivered through the hooks of worker programs, which each worker ingredient renders and maps onto the
   native events that carry each trigger ([0009](0009-agent-support.md)), except where point 7 places them in an
   attempt's prompt. Where a worker program lacks the event a trigger needs, nothing is rendered for it and the health
   check reports the gap.
3. An entry may be declared static. A static entry is written as a convention block in `AGENTS.md`
   ([0018](0018-conventions.md)), works without the binary, and is not also delivered by hook.
4. Entries come only from versioned repository files, skills included: a skill is delivered as the file committed under
   `.agents/skills/`, and the health check reports an entry that names a skill the repository does not have. Nothing is
   fetched when a hook runs. For a worker the binary starts unattended, entries are read from the session's base commit
   ([0011](0011-safety-invariants.md)).
5. Always-on content has a budget per role. The health check reports a role whose always-on content exceeds it, with
   the size of each entry.
6. A deterministic preview shows what would be delivered for a role, a set of files and a prompt, and which trigger
   matched each entry.
7. In every attempt whose prompt reaches the worker through the binary on standard input, whether the attempt is run
   on its own, for a lifecycle action or as a recipe step, the binary places the always entries of the unattended role
   before the task's text, each headed by the line that names it, and the session-start hook of that attempt delivers
   no always entry. When the caller passes the prompt among the worker's arguments, the binary adds nothing to it, and
   the session-start hook delivers the always entries. Path and prompt entries are delivered by hooks in every case.
   Entries placed in the prompt count against the budget of point 5, and turning delivery off turns off both channels.

## Reasons

- **1.** Session start, a file path and a word in a prompt are mechanical facts, so the same session gives the same
  delivery every time ([0002](0002-division-of-work.md)). Known guidance applies because a trigger matched, not because
  someone remembered to name it or the model chose it. Path and prompt triggers confine the cost of narrow guidance to
  the sessions where it applies. The role split serves the two audiences with their own directives. A pointer costs one
  line and leaves the reading to the worker; content costs its full length and guarantees the text is present.
  Delivering a path or prompt entry once per session keeps repeated matches from repeating the same text.
- **2.** In a session a person drives, a hook is the only point where text enters a worker's context without the
  person typing it, and worker ingredients already render hooks per worker program ([0009](0009-agent-support.md)).
  Reporting a missing event, instead of emulating it, keeps the difference between programs visible.
- **3.** Hook delivery depends on the binary being cached on the machine ([0017](0017-engine-version.md)). A directive
  that must reach every worker in every clone belongs where workers read without the tool, and as a convention block it
  survives an eject. Delivering it by hook as well would pay its cost twice.
- **4.** Delivered text acts as instructions, so it may come only from sources that are reviewed: files committed in the
  repository, which a skill is like any other. How a skill got there is the team's own step, and the binary reads no
  other tool's files, such as an installer's lock file ([0022](0022-tool-integration.md)), so the commit is what makes a
  skill reviewed. Fetching at hook time would make each hook depend on the network and the 50 ms budget
  ([0003](0003-implementation-language.md)), and would deliver content no one reviewed. Reading from the base commit for
  unattended workers means a worker cannot rewrite the instructions of its next attempt at a task, as with the rest of
  the data that drives it ([0011](0011-safety-invariants.md)). Reporting a named skill that does not exist tells the
  team that a declared entry delivers nothing. The trade: a skill edited by hand after a team installed it is delivered
  as edited, once committed.
- **5.** Every always-on line costs context in every session of the role. A budget makes that cost visible as a number.
  A report rather than a refusal leaves the trade to the repository.
- **6.** What a hook adds to a session is invisible from outside it. A preview computed by the same rules lets a person
  or a worker check the triggers before relying on them.
- **7.** A worker program may run no hook unattended (Context), so hook delivery alone would leave an attempt without
  its unattended directives, the guidance written for exactly the sessions nobody watches. A prompt on standard input
  passes through the binary before the worker starts (Context), so the binary can place the entries there, where the
  worker reads its task. Withholding the hook's always entries in that attempt keeps the text from arriving twice, as
  point 3 does for static entries. The caller's arguments are opaque to the binary (Context), so a prompt passed there
  is left as it is and the hook remains the channel. Path and prompt triggers fire on what happens inside the session,
  which the binary cannot know when it writes the prompt, so they stay with hooks. The trade: an attempt whose caller
  passes the prompt among the arguments, to a worker program that runs no hook unattended, receives no always entry.

## Rejected alternatives

### Rely on a coding agent's description-based skill loading

At its best, there is nothing to configure and the model weighs relevance with full knowledge of the request. It loses
because loading stays the model's choice, so people keep invoking known guidance by hand, which is the problem this
decision exists to remove.

### Put all guidance in the instruction file

At its best, it works for every worker and in every clone without hooks or the binary. It loses because every line is
read in every session, so guidance for a few files or one topic costs context everywhere, and one file cannot give
interactive sessions and attempts different directives.

### Each worker program's native mechanisms only

Some coding agents scope rule files by path or load memory files per directory. At its best, no hooks are needed for the
programs that have them. It loses because the mechanisms differ between programs and some have none, so one declaration
would become one configuration per program ([0009](0009-agent-support.md)).

### Hooks only, in attempts too

The always entries reach an attempt through the session-start hook, as in a session a person drives. At its best, one
channel serves every session, and the prompt stays exactly what the caller wrote. It loses because a worker program
that runs no hook unattended (Context) would receive no always entry in any attempt, and nothing the repository or the
binary can do makes the program run it.

## Consequences

Costs:

- Always-on guidance costs context in every session of its role and can dilute a coding agent's attention.
- Prompt words match text, not intent: a listed word can match a message it does not concern, and a message about the
  topic in other words does not match.
- A skill delivered by a trigger can also be loaded by a coding agent through its description, and then appears twice.
- On a machine where the pinned release is not cached, hook-delivered guidance is absent
  ([0017](0017-engine-version.md)); static entries cover the directives that must always be present.
- Guidance differs between worker programs where their hook events differ.
- An attempt's prompt is longer than the caller's text by the always entries of the unattended role, and an attempt
  whose caller passes the prompt among the worker's arguments, to a worker program that runs no hook unattended,
  receives no always entry.

Must be maintained:

- For each worker ingredient, in the cookbook that publishes it, the hook events that carry each trigger.
- The declaration format and the preview, as part of the public contract ([0006](0006-repository-contract.md)).

## Confirmation

- In a fixture session, session start delivers the always entries of the role; the first read of a matching file
  delivers the path entry once; a prompt containing a listed word delivers the prompt entry; a session that matches
  nothing receives nothing.
- A static entry appears as a convention block in `AGENTS.md` and in no hook output.
- An entry that names a skill absent from the fixture is reported by the health check.
- A fixture worker that edits a skill in its working copy during an attempt receives the base commit's skill on its next
  attempt.
- No hook makes a network call.
- In a fixture attempt whose worker edits a directive file, the next attempt receives the base commit's content.
- The preview's output equals what the hooks deliver for the same role, files and prompt.
- The health check reports a role whose always-on content exceeds its budget.
- In a fixture attempt whose prompt arrives on standard input, from the binary's own attempt, a lifecycle action and a
  recipe step alike, the worker's prompt starts with the always entries of the unattended role, each headed by its
  naming line, followed by the task's text, and the session-start hook of that attempt delivers no always entry.
- In a fixture attempt whose prompt is among the worker's arguments, the arguments reach the worker unchanged, and the
  session-start hook delivers the always entries.
- In both fixtures, a path entry is delivered by the hook that matches its file.
