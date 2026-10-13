import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration

return new DeclaredRecipe(new RecipeDeclaration('agentic/context', []), { kitchen, book, options ->
  def host = kitchen.recipe('context')
  host.application('agentic.context/default')
  host
})
