import thecook.cookbook.ingredients.agentpolicy.AgentInstructions
import thecook.cookbook.ingredients.agentpolicy.PolicyFile
import thecook.cookbook.ingredients.git.Git
import thecook.cookbook.ingredients.skills.Skills
import thecook.cookbook.ingredients.thecook.TheCook
import thecook.cookbook.recipes.agentic.claude.ClaudeFiles
import thecook.cookbook.recipes.agentic.codex.CodexFiles

return { kitchen, book, Map preferences ->
  def unknown = preferences.keySet().findAll { !(it in ['policy', 'skills-home', 'adopt']) }
  if (unknown) throw new IllegalArgumentException('Unknown recipe argument: ' + unknown.join(', '))
  if (!preferences.policy) throw new IllegalArgumentException('Required argument: --policy')
  def adoption = preferences.containsKey('adopt') ? preferences.adopt : 'false'
  if (!(adoption in ['true', 'false'])) throw new IllegalArgumentException('--adopt must be true or false')
  def adopt = adoption == 'true'

  def repository = kitchen.repository.directory
  def selectedPolicy = new File(preferences.policy.toString())
  if (!selectedPolicy.isAbsolute()) selectedPolicy = new File(repository, selectedPolicy.path)
  def policy = PolicyFile.read(selectedPolicy)
  def source = new File(preferences['skills-home'] ?: new File(System.getProperty('user.home'), '.agents/skills').path)
  if (!source.isAbsolute()) source = new File(repository, source.path)
  def names = ['solid', 'hexagonal-layout', 'effective-java-core']
  names.each { name ->
    if (!new File(source, name + '/SKILL.md').isFile())
      throw new IllegalArgumentException('Shared skill is missing SKILL.md: ' + new File(source, name))
  }

  def skills = new Skills()
  def claudeSkills = new Skills('claude-skills', '.claude/skills')
  skills.adopt = adopt
  claudeSkills.adopt = adopt
  names.each { name ->
    skills.link(name, new File(source, name).absolutePath)
    claudeSkills.link(name, '../../.agents/skills/' + name)
  }

  def instructions = new AgentInstructions()
  instructions.instructions.addAll([
    '# Working on the-cook',
    '',
    'Use .agents/skills/solid for implementation and review.',
    'Use .agents/skills/hexagonal-layout for application boundaries and adapters.',
    'Use .agents/skills/effective-java-core for Java and Groovy API design.',
    '',
    'Core contracts and the CLI are Kotlin; cookbook suppliers and repository recipes are Groovy.',
    'Keep application rules independent of filesystem and terminal adapters.',
    'Change this setup in cookbook/recipes/cookbook/recipe.groovy and the selected policy YAML.',
    'Build and verify changes with ./gradlew check cliBuild.',
    'Preview agent setup with ./cook cookbook --policy=.agents/policy.yaml --dry-run.'
  ])
  instructions.instructions.addAll(policy.instructions)

  def cook = new TheCook()
  def git = new Git()
  git.ignore.add(cook.files.local)
  git.ignore.add(skills.files.local)
  git.ignore.add(claudeSkills.files.local)

  def host = kitchen.recipe('cookbook')
  host.application('agentic.init/cookbook')
  def include = { component, contributor ->
    component.contribute { contributor }
    host.includes(component)
  }
  [cook, git, skills, claudeSkills, instructions].each { contributor ->
    def component = kitchen.ingredient(contributor.id.value)
    component.application('agentic.init/' + contributor.id.value)
    include(component, contributor)
  }
  [new CodexFiles(policy, adopt), new ClaudeFiles(policy, adopt)].each { renderer ->
    def component = kitchen.recipe(renderer.id.value)
    component.application('agentic.permissions/' + (renderer instanceof CodexFiles ? 'codex' : 'claude'))
    include(component, renderer)
  }
  host
}
