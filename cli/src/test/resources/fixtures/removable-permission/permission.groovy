import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput
import thecook.model.RecipeRemoval

def harness = new RecipeInput.RequiredChoices('harness', ['claude', 'codex'])
return new DeclaredRecipe(new RecipeDeclaration('permission', [new RecipeInput.RequiredPath('policy'), harness],
new RecipeRemoval(harness, [claude: ['agentic.permissions/claude'], codex: ['agentic.permissions/codex']],
['policy'] as Set)), { kitchen, book, options ->
  def source = new File(kitchen.repository.directory, options.policy)
  def selected = options.harness[0]
  def component = kitchen.recipe(selected)
  component.application('agentic.permissions/' + selected)
  component.output('settings.json', [format: 'json']) {
    key([selected], [adopt: true]) { source.text.trim() }
  }
  component
})
