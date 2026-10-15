# skills/solid

Content: the `solid` skill, shipped as every file under `skill/` in this artifact. It writes nothing on its own. A
recipe hands it to the `agents` model, which places it under `.agents/skills/solid/`, and `connect` points each
harness at it.

```groovy
@ingredient(Agents) def agents
@ingredient(Solid) def solid
agents.skills(solid)
```
