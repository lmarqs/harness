package dev.thecook.skills

import dev.thecook.agents.Skill

/**
 * Supplies the hexagonal layout skill for business logic boundaries and dependency direction.
 * It writes nothing on its own; Agents installs its content as a shared skill.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.claude.Claude
 * import dev.thecook.skills.HexagonalLayout
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
  /** The resource directory name and the shared skill directory under `.agents/skills/`. */
  final String name = 'hexagonal-layout'
}
