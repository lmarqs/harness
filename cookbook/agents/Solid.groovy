package dev.thecook.agents

/**
 * Supplies the SOLID principles, TDD and clean code skill for implementation and review.
 * It writes nothing on its own; Agents installs its content as a shared skill.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.agents.Claude
 * import dev.thecook.agents.Solid
 *
 * @ingredient(Agents) def agents
 * @ingredient(Claude) def claude
 * @ingredient(Solid) def solid
 *
 * agents.skills(solid)
 * agents.connect([claude])
 * ```
 */
@Ingredient
class Solid implements Skill {
  /** The skill directory name, under `skills/` in this artifact and under `.agents/skills/` in the destination. */
  final String name = 'solid'
}
