# 0020. Guidance enters agent context through declared deterministic triggers

## Objective

**Problem.** Repositories keep guidance that agents should follow, but an agent loads it only when the model judges it
relevant. People retype the same invocations at the start of sessions, and guidance someone forgets to invoke is not
applied. Putting all of it in the instruction file instead costs context in every session, including the many where it
does not apply.

**Goal.** Known guidance reaches every session that needs it without anyone invoking it by hand, and costs nothing in
the sessions that do not need it.

## Context

- Repositories keep guidance as skills and directive files: coding guidelines, a SQL review checklist, rules for dates
  and timezones. An agent loads a skill when the model judges the skill's description relevant to the request; whether
  a given skill is loaded for a given request is the model's choice.
- People invoke known guidance by name at the start of sessions because description matching does not load it
  reliably. The cost is repeated typing, and guidance that is forgotten is not applied.
- Much guidance applies to specific files (SQL rules to migrations) or topics (timezone rules to date handling).
  Instruction files are read at the start of every session ([0018](0018-conventions.md)), so guidance placed there
  costs context in every session, including the many where it does not apply.
- Agents expose hook points where a hook can add text to the agent's context. Claude Code 2.1.x has them at session
  start, which it fires again after compacting a conversation, after a tool use, and when the person submits a prompt.
  The set of events differs between agents ([0009](0009-agent-support.md)).
- Interactive sessions and unattended agents need different directives: a rule for how an unattended agent reports a
  blocked task means nothing to a person at the terminal.
- Skills are installed by a skills installer that records each skill's content hash in a lock file; a reinstall can
  change a skill's content.
- Text added to an agent's context acts as instructions. A source that someone can change without review is a channel
  for instructions; an unattended agent can write files in its working copy, skills and directive files included
  ([0011](0011-safety-invariants.md)).
- A hook invocation adds at most 50 ms at p95 ([0003](0003-implementation-language.md)), and hooks do nothing on a
  machine where the pinned release is not cached ([0017](0017-engine-version.md)).

## Decision

1. The repository declares guidance entries. Each entry is an installed skill or a repository file, with one trigger:
   always (at session start, including when the agent fires session start again after compaction), file paths (the
   first time in a session that the agent reads or edits a matching file), or prompt words (the person's message
   contains one of the listed words or phrases). Each entry applies to the interactive role, the unattended role or
   both, and is delivered either as its content or as a one-line pointer to it. A path or prompt entry is delivered at
   most once per session.
2. Adapters deliver entries through agent hooks ([0009](0009-agent-support.md)). Where an agent lacks the event a
   trigger needs, nothing is rendered for it and the health check reports the gap.
3. An entry may be declared static. A static entry is written as a convention block in `AGENTS.md`
   ([0018](0018-conventions.md)), works without the binary, and is not also delivered by hook.
4. Entries come only from versioned repository files and installed skills. A skill whose content does not match its
   locked hash is not delivered, and the health check reports it. Nothing is fetched when a hook runs. For unattended
   agents, entries are read from the session's base commit ([0011](0011-safety-invariants.md)).
5. Always-on content has a budget per role. The health check reports a role whose always-on content exceeds it, with
   the size of each entry.
6. A deterministic preview shows what would be delivered for a role, a set of files and a prompt, and which trigger
   matched each entry.

## Reasons

- **1.** Session start, a file path and a word in a prompt are mechanical facts, so the same session gives the same
  delivery every time ([0002](0002-division-of-work.md)); known guidance applies because a trigger matched, not because
  someone remembered to name it or the model chose it. Path and prompt triggers confine the cost of narrow guidance to
  the sessions where it applies. The role split serves the two audiences with their own directives. A pointer costs one
  line and leaves the reading to the agent; content costs its full length and guarantees the text is present. Delivering
  a path or prompt entry once per session keeps repeated matches from repeating the same text.
- **2.** A hook is the only point where text enters an agent's context without the person typing it, and adapters
  already render hooks per agent ([0009](0009-agent-support.md)). Reporting a missing event, instead of emulating it,
  keeps the difference between agents visible.
- **3.** Hook delivery depends on the binary being cached on the machine ([0017](0017-engine-version.md)). A directive
  that must reach every agent in every clone belongs where agents read without the tool, and as a convention block it
  survives an eject. Delivering it by hook as well would pay its cost twice.
- **4.** Delivered text acts as instructions, so it may come only from sources that are reviewed: versioned files, or
  skills whose content matches the hash recorded when they were installed. Fetching at hook time would make each hook
  depend on the network and the 50 ms budget ([0003](0003-implementation-language.md)), and would deliver content no one
  reviewed. Reading from the base commit for unattended agents means a worker cannot rewrite its own instructions
  between attempts, as with the rest of the data that drives it ([0011](0011-safety-invariants.md)).
- **5.** Every always-on line costs context in every session of the role. A budget makes that cost visible as a number;
  a report rather than a refusal leaves the trade to the repository.
- **6.** What a hook adds to a session is invisible from outside it. A preview computed by the same rules lets a person
  or an agent check the triggers before relying on them.

## Rejected alternatives

### Rely on the agent's description-based skill loading

At its best, there is nothing to configure and the model weighs relevance with full knowledge of the request. It loses
because loading stays the model's choice, so people keep invoking known guidance by hand, which is the problem this
decision exists to remove.

### Put all guidance in the instruction file

At its best, it works in every agent and every clone without hooks or the binary. It loses because every line is read
in every session, so guidance for a few files or one topic costs context everywhere, and one file cannot give
interactive and unattended agents different directives.

### Each agent's native mechanisms only

Some agents scope rule files by path or load memory files per directory. At its best, no hooks are needed for the
agents that have them. It loses because the mechanisms differ between agents and some have none, so one declaration
would become one configuration per agent ([0009](0009-agent-support.md)).

## Consequences

Costs:

- Always-on guidance costs context in every session of its role and can dilute the agent's attention.
- Prompt words match text, not intent: a listed word can match a message it does not concern, and a message about the
  topic in other words does not match.
- A skill delivered by a trigger can also be loaded by the agent through its description, and then appears twice.
- On a machine where the pinned release is not cached, hook-delivered guidance is absent
  ([0017](0017-engine-version.md)); static entries cover the directives that must always be present.
- Guidance differs between agents where their hook events differ.

Must be maintained:

- For each adapter, the hook events that carry each trigger.
- The declaration format and the preview, as part of the public contract ([0006](0006-repository-contract.md)).

## Confirmation

- In a fixture session, session start delivers the always entries of the role; the first read of a matching file
  delivers the path entry once; a prompt containing a listed word delivers the prompt entry; a session that matches
  nothing receives nothing.
- A static entry appears as a convention block in `AGENTS.md` and in no hook output.
- A skill whose content differs from its locked hash is not delivered and is reported by the health check.
- No hook makes a network call.
- In an unattended fixture whose agent edits a directive file, the next attempt receives the base commit's content.
- The preview's output equals what the hooks deliver for the same role, files and prompt.
- The health check reports a role whose always-on content exceeds its budget.
