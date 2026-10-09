# Skills

Skills are folders of instructions agents load on demand. `harness` handles two things about them: it installs and
refreshes its own skill, the **harness skill**, in every adopted repository; and it installs the shared skill sources
a repository declares, by delegating to the [`skills` CLI](https://github.com/vercel-labs/skills). It never writes a
domain skill.

## The harness skill

`.agents/skills/harness/SKILL.md` is a tool-owned file, written by `scaffold` and refreshed by every `render`. A skill
that differs only because the binary is newer is a [`doctor`](doctor.md) note, not drift. It
carries the same text as [`harness explain`](explain.md): the standards `harness` applies and the reasoning behind
each, written for an agent.

It teaches an agent to:

- edit `.agents/harness.yaml`, `.agents/policy.yaml` and `.agents/hooks.yaml` correctly, then run `harness render`;
- place a policy entry in `allow`, `ask`, `deny` or `worker.deny`, and explain why;
- finish an adoption from the `harness doctor --json` to-do;
- choose lanes and write skip reasons for `harness verify`;
- run and resume Ralph, and report blocked work;
- fire lifecycle events with `harness lifecycle run <event>`, including delivering a Ralph run's commits after
  arranging them for review.

Agents find it where they look for skills:

| Agent | Path | Shape |
| --- | --- | --- |
| Claude Code | `.claude/skills/harness` | a link to `../../.agents/skills/harness` |
| Codex | `.codex/skills/harness` | a link to `../../.agents/skills/harness` |

When `.claude/skills` (or `.codex/skills`) is already a link to the whole `.agents/skills` directory, the skill is
reached through it and no extra link is written. When it is a real directory, the link is added inside it and nothing
else there changes.

The skill is not listed in the `skills` CLI's lock file, so that CLI does not manage it.

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

Files read: `.agents/harness.yaml`. Files written: by the `skills` CLI, `.agents/skills/<skill>/` and
`skills-lock.json`.

### Examples

```yaml
# .agents/harness.yaml (a Node API)
modules: { skills: true }
skills:
  sources:
    - lmarqs/skills
    - git@github.com:acme/agent-skills.git
```

```sh
harness skills sync
git add .agents/skills skills-lock.json
git commit -m "chore: install shared agent skills"
```

On a Terraform or Go repository whose machine has no Node:

```sh
$ harness skills sync
Node is not available. Run these on a machine with Node, then commit the result:
  npx skills add lmarqs/skills
$ echo $?
3
```

Across a fleet:

```sh
harness fleet skills sync --deliver
```

## Guidelines

- Do keep domain knowledge in repository skills you write and review; `harness` never generates them.
- Do commit installed skills and the lock file, so every clone and every agent has them without running anything.
- Don't edit `.agents/skills/harness/SKILL.md`; render overwrites it. Put repository-specific guidance in `AGENTS.md`
  or a repository skill.
- Don't vendor the same skill by hand and through a source; the `skills` CLI will not know about the hand copy.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `doctor`: source pending | Not installed yet, or installed by hand outside the lock file | `harness skills sync` |
| Claude Code does not list the harness skill | `.claude/skills/harness` missing, or the folder is not trusted | `harness render`; trust the folder |
| A private source fails | No git credentials for it on this machine | Configure access, rerun |
