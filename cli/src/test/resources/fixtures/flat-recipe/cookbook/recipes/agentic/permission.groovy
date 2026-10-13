import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput

return new DeclaredRecipe(
    new RecipeDeclaration('agentic/permission', [
      new RecipeInput.RequiredPath('policy'),
      new RecipeInput.RequiredChoices('harness', ['codex', 'claude'])
    ]), { kitchen, book, Map options ->
      def policy = new File(options.policy)
      if (!policy.isAbsolute()) {
        policy = new File(kitchen.repository.directory, policy.path)
      }
      def target = kitchen.recipe('permission')
      target.output('selection.json', [format: 'json']) {
        key(['policy']) { policy.text.trim() }
        key(['harness']) { options.harness.join(',') }
      }
      target
    })
