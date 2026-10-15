package dev.thecook.agents

/**
 * Supplies the hexagonal layout skill for business logic boundaries and dependency direction.
 * It writes nothing on its own; Agents installs its content as a shared skill.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.agents.Claude
 * import dev.thecook.agents.HexagonalLayout
 *
 * @ingredient(Agents) def agents
 * @ingredient(Claude) def claude
 * @ingredient(HexagonalLayout) def hexagonalLayout
 *
 * agents.skills(hexagonalLayout)
 * agents.connect([claude])
 * ```
 */
@Ingredient
class HexagonalLayout implements Skill {
  /** The skill directory name, under `skills/` in this artifact and under `.agents/skills/` in the destination. */
  final String name = 'hexagonal-layout'
}
