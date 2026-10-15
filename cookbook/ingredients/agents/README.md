# agents

Models the repository conventions shared by every coding agent: `AGENTS.md`, the policy at `.agents/policy.yaml`
and the shared skills under `.agents/skills/`. Defines the types the harness models and the skill content build on:
`Harness`, `Skill`, `Policy` and `Command`.

## Model

- `instruct(lines)` adds instruction lines.
- `skills(skill...)` adds skill content shipped by other ingredients.
- `policy()` reads the shared policy, or the starter when the file is absent. Unknown fields and any version other
  than 1 are rejected.
- `permit(harnesses)` renders the shared policy into each harness's own permission model.
- `connect(harnesses)` points each harness at `AGENTS.md` and at every added skill, by paths relative to the
  harness's directory. Call it after adding skills.

## The harness seam

A harness is an agent program's model, such as `claude` or `codex`. It implements `Harness`: `permit(policy)`,
`readInstructions(path)` and `readSkills(path, names)`. This model owns the shared layout and computes every path it
hands over, so a harness never names `AGENTS.md` or `.agents/skills` on its own. A new harness is one new
ingredient; the recipes that loop over harnesses only add it to their list.

## Skill content

A skill is an ingredient that implements `Skill` and names itself. Its files are everything its artifact packages
under `skill/`, with `SKILL.md` among them.

## Writes

| Target | Part |
| --- | --- |
| `AGENTS.md` | Seeded with a title when absent, once instructions were added or a harness connected; the section `instructions` when instructions were added |
| `.agents/policy.yaml` | Seeded with the starter policy when absent, once the policy was read; never owned |
| `.agents/skills/<name>/**` | One file tree per added skill |
