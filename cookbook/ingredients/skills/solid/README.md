# skills/solid

Content: the `solid` skill, shipped as files inside this artifact. It writes nothing on its own. A recipe hands it to
the `agents` model, which places it under `.agents/skills/solid/`, and a harness model links it where that harness
looks.

```groovy
@ingredient(Agents) def agents
@ingredient(Solid) def solid
agents.skills(solid)
```
