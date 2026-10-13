import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput
import thecook.model.RecipeRemoval
import thecook.reconciliation.StateSchema

def harnessInput = new RecipeInput.RequiredChoices('harness', ['codex', 'claude'])
return new DeclaredRecipe(new RecipeDeclaration('agentic/init', [harnessInput], new RecipeRemoval(harnessInput,
[codex: ['agentic.permissions/codex', 'agentic.context/codex'],
  claude: ['agentic.permissions/claude', 'agentic.context/claude']])), { kitchen, book, options ->
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
    host.includes(book.recipeWith('agentic/permission', kitchen,
        [policy: '.agents/policy.yaml', harness: unconfigured], policy))
  }
  host.includes(book.recipe('agentic/context', kitchen, [harness: options.harness]))
  host
})
