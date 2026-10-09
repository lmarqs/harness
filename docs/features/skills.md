# Skills

Skills are folders of instructions agents load on demand. `harness` handles three things about them: it installs and
refreshes its own skill, the **harness skill**, in every adopted repository; it installs the shared skill sources a
repository declares, by delegating to the [`skills` CLI](https://github.com/vercel-labs/skills); and it injects
installed skills into agent sessions when the repository declares them as [standing context](context.md). It never
writes a domain skill.

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## The harness skill

`.agents/skills/harness/SKILL.md` is a tool-owned file, written by `scaffold` and refreshed by every `render`. Like
every owned file, it is drift-checked by the pinned release: it must match the text that release embeds, and an
upgrade refreshes it in the same change that moves the pin ([wrapper](wrapper.md#upgrading),
[ADR 0007](../adrs/0007-ownership.md)). Its header is a YAML comment on the line after the opening `---` of its front
matter, so agents and the `skills` CLI still read the front matter. It carries the same text as
[`harness explain`](explain.md): the standards `harness` applies and the reasoning behind each, written for an agent.

It teaches an agent to:

- run every `harness` command through the repository's wrapper, `./harnessw <command>`, from the root of the checkout
  it works in;
- edit `.harness/config.yaml`, `.harness/policy.yaml` and `.harness/hooks.yaml` correctly, then run
  `./harnessw render`;
- place a policy entry in `allow`, `ask`, `deny` or `worker.deny`, and explain why;
- finish an adoption from the `harness doctor --json` to-do;
- choose lanes and write skip reasons for `harness verify`;
- run and resume Ralph, and report blocked work;
- fire lifecycle events with `harness lifecycle run <event>`, including delivering a Ralph run's commits after
  arranging them for review;
- declare lifecycle and notification actions of the right kind (`run`, `http` or `agent`), with secrets read from
  environment variables;
- declare standing context: which skill or directive applies always, on which paths, or on which words, and preview
  it with `./harnessw context show`.

Agents find it where they look for skills:

| Agent | Path | Shape |
| --- | --- | --- |
| Claude Code | `.claude/skills/harness` | a link to `../../.agents/skills/harness` |
| Codex | `.codex/skills/harness` | a link to `../../.agents/skills/harness` |

When `.claude/skills` (or `.codex/skills`) is already a link to the whole `.agents/skills` directory, the skill is
reached through it and no extra link is written. When it is a real directory, the link is added inside it and nothing
else there changes.

The skill is not listed in the `skills` CLI's lock file, so that CLI does not manage it. `harness remove` and
`harness remove --eject` both delete it and its links: it teaches `./harnessw` commands that do not exist once the
wrapper is gone ([remove](remove.md#eject)).

## Declared skill sources

### When to use it

- The repository should carry skills your organization or you maintain elsewhere.
- Many repositories should get the same skill: declare the source in each profile, or run `harness fleet skills sync`.

Needs `modules.skills: true`.

### How it works

`harness skills sync` reads `skills.sources` from the [profile](profile.md#skills) and, for each source, runs the
`skills` CLI's `add` command through `npx`. The `skills` CLI installs the skills into `.agents/skills/` and records them
in its lock file, `skills-lock.json`. Both are committed by the repository like any other knowledge.

| Situation | Behaviour |
| --- | --- |
| Node and `npx` available | each source is installed; the CLI's output is shown |
| No Node on this machine | nothing is installed; the exact commands are printed for a machine with Node; exit 3 |
| A source is already installed | the `skills` CLI decides whether to update it |

`harness doctor` reports a declared source as pending while the lock file lists no skill from it.

`skills sync` never removes a skill. To drop one, remove it from `skills.sources` and remove the skill with the
`skills` CLI.

### Reference

```text
harness skills sync
```

| Exit code | Meaning |
| --- | --- |
| 0 | Every source installed or already present |
| 1 | The `skills` CLI failed for a source |
| 3 | Node is not available; run the printed commands on a machine that has it |

Files read: `.harness/config.yaml`. Files written: by the `skills` CLI, `.agents/skills/<skill>/` and
`skills-lock.json`.

### Examples

```yaml
# .harness/config.yaml (a Node API)
modules: { skills: true }
skills:
  sources:
    - lmarqs/skills
    - git@github.com:acme/agent-skills.git
```

```sh
./harnessw skills sync
git add .agents/skills skills-lock.json
git commit -m "chore: install shared agent skills"
```

On a Terraform or Go repository whose machine has no Node:

```sh
$ ./harnessw skills sync
Node is not available. Run these on a machine with Node, then commit the result:
  npx skills add lmarqs/skills
$ echo $?
3
```

Across a fleet:

```sh
./harnessw fleet skills sync --deliver
```

## Skills as standing context

An installed skill reaches a session in one of two ways:

| Way | When the agent gets it | Decided by |
| --- | --- | --- |
| The agent matches the skill's description and loads it | when the agent judges it relevant | the agent, from the description |
| A [`context`](context.md) entry names it | always, when matching files are read or edited, or when the person's message contains a listed word | the repository, deterministically |

A `context` entry names a skill by its directory under `.agents/skills/`. When `skills-lock.json` lists the skill,
`harness` injects it only while its content matches the hash the lock records, so a hand edit to an installed skill
stops its injection until it is reinstalled; `harness doctor` reports the mismatch as a finding. A repository's own
skills, which the lock does not list, are injected as committed. Nothing is fetched when a hook runs: a skill is
injected only once it is installed and committed.

Skills written to be injected follow the [skills standards](../ux/skills-standards.md): a short body, self-contained,
instructions first, so they read correctly as a pointer or as injected content.

## Guidelines

- Do keep domain knowledge in repository skills you write and review; `harness` never generates them.
- Do commit installed skills and the lock file, so every clone and every agent has them without running anything.
- Don't edit `.agents/skills/harness/SKILL.md`; render overwrites it. Put repository-specific guidance in `AGENTS.md`
  or a repository skill.
- Don't vendor the same skill by hand and through a source; the `skills` CLI will not know about the hand copy.
- Don't edit a skill installed from a source in place when it is injected as standing context: its content then
  differs from the lock, and the skill is not injected. Change it at its source and reinstall.
- Do declare as standing context the skills that should apply without anyone invoking them, and leave the rest to the
  agent's own matching.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `doctor`: source pending | Not installed yet, or installed by hand outside the lock file | `harness skills sync` |
| Claude Code does not list the harness skill | `.claude/skills/harness` missing, or the folder is not trusted | `harness render`; trust the folder |
| A private source fails | No git credentials for it on this machine | Configure access, rerun |
| `doctor`: an injected skill does not match its lock | The installed skill was edited by hand, or installed outside the `skills` CLI | `harness skills sync`, or update it through the `skills` CLI |
