package dev.thecook.agents

/**
 * Supplies the Effective Java skill for Java and Groovy API design.
 * It writes nothing on its own; Agents installs its content as a shared skill.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.agents.Claude
 * import dev.thecook.agents.EffectiveJavaCore
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
  /** The skill directory name, under `skills/` in this artifact and under `.agents/skills/` in the destination. */
  final String name = 'effective-java-core'
}
