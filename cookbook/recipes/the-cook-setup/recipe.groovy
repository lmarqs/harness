@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex
@ingredient(Git) def git
@ingredient(TheCook) def cook
@ingredient(Solid) def solid
@ingredient(HexagonalLayout) def hexagonal
@ingredient(EffectiveJavaCore) def effectiveJava

def policy = agents.policy()

agents.skills(solid, hexagonal, effectiveJava)
agents.instruct(
  '# Working on the-cook',
  '',
  'Use .agents/skills/solid for implementation and review.',
  'Use .agents/skills/hexagonal-layout for application boundaries and adapters.',
  'Use .agents/skills/effective-java-core for Java and Groovy API design.',
  '',
  'Core contracts and the CLI are Kotlin; cookbook ingredients and recipes are Groovy.',
  'Build and verify changes with ./gradlew check cliBuild.')
agents.instruct(policy.instructions)

claude.permit(policy)
claude.importAgents()
claude.skills(agents)

codex.permit(policy)

git.ignore(cook.localFiles)
git.ignore(claude.localFiles)
