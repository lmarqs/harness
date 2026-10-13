import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput
import thecook.reconciliation.StateSchema

return new DeclaredRecipe(new RecipeDeclaration('agentic/init', [new RecipeInput.RequiredChoices('harness', ['codex', 'claude'])]), { kitchen, book, options ->
  def configured = StateSchema.current(kitchen.repository.state()).applications.keySet()
  def unconfigured = options.harness.findAll { !configured.contains('agentic.permissions/' + it) }
  def policySeed = 'version: 1\ninstructions: []\ncommands: {}\n'
  def policyFile = null
  def policy = null
  if (unconfigured) {
    policyFile = kitchen.repository.target('.agents/policy.yaml')
    def policyType = book.type('ingredients/agent-policy/PolicyFile.groovy')
    try {
      policy = policyFile.exists() ? policyType.read(policyFile) : policyType.readText(policySeed)
    } catch (Exception error) {
      throw new IllegalArgumentException("Invalid starter policy .agents/policy.yaml; expected, for example:\n${policySeed}", error)
    }
  }

  def host = kitchen.recipe('init')
  host.application('agentic.init/default')
  if (unconfigured) {
    if (!policyFile.exists()) {
      host.output('.agents/policy.yaml', [seed: true]) {
        text {
          policySeed
        }
      }
    }
    def permissions = kitchen.recipe('permission')
    permissions.application('agentic.permissions/default')
    unconfigured.each { harness ->
      def renderer = book.type("recipes/agentic/${harness}/${harness.capitalize()}Files.groovy")
          .newInstance(policy, false)
      if (harness == 'claude') {
        renderer.instructions = null
      }
      def child = kitchen.recipe(renderer.id.value)
      child.application('agentic.permissions/' + harness)
      child.contribute { renderer }
      permissions.includes(child)
    }
    host.includes(permissions)
  }
  host.includes(book.recipe('agentic/context', kitchen, [harness: options.harness]))
  host
})
