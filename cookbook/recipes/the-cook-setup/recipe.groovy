@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex
@ingredient(Git) def git
@ingredient(Solid) def solid
@ingredient(HexagonalLayout) def hexagonal
@ingredient(EffectiveJavaCore) def effectiveJava

def harnesses = [claude, codex]

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
agents.instruct(agents.policy().instructions)
agents.permit(harnesses)
agents.connect(harnesses)

git.ignore(claude.localFiles)
