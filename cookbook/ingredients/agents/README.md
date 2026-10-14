# agents

Models the repository conventions shared by every coding agent: `AGENTS.md`, the policy at `.agents/policy.yaml`
and the shared skills under `.agents/skills/`. Defines the `Policy` and `Skill` types that the harness models and the
skill content consume.

## Model

- `instruct(lines)` adds instruction lines.
- `skills(skill...)` adds skill content shipped by other ingredients.
- `policy()` reads the shared policy, or the starter when the file is absent. Unknown fields and any version other
  than 1 are rejected.

## Writes

| Target | Part |
| --- | --- |
| `AGENTS.md` | Seeded with a title when absent; the section `instructions` when instructions were added |
| `.agents/policy.yaml` | Seeded with the starter policy when absent; never owned |
| `.agents/skills/<name>/**` | One file tree per added skill |

Injecting this ingredient establishes the convention, so both seeds apply whenever a recipe injects it.
