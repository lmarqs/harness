package dev.thecook.skills

import dev.thecook.agents.Skill

/**
 * Supplies the SOLID principles, TDD and clean code skill for implementation and review.
 * It writes nothing on its own; Agents installs its content as a shared skill.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.claude.Claude
 * import dev.thecook.skills.Solid
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
  /** The resource directory name and the shared skill directory under `.agents/skills/`. */
  final String name = 'solid'
}
