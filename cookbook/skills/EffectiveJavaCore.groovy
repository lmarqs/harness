package dev.thecook.skills

import dev.thecook.agents.Skill

/**
 * Supplies the Effective Java skill for Java and Groovy API design.
 * It writes nothing on its own; Agents installs its content as a shared skill.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.claude.Claude
 * import dev.thecook.skills.EffectiveJavaCore
 *
 * @ingredient(Agents) def agents
 * @ingredient(Claude) def claude
 * @ingredient(EffectiveJavaCore) def effectiveJavaCore
 *
 * agents.skills(effectiveJavaCore)
 * agents.connect([claude])
 * ```
 */
@Ingredient
class EffectiveJavaCore implements Skill {
  /** The resource directory name and the shared skill directory under `.agents/skills/`. */
  final String name = 'effective-java-core'
}
