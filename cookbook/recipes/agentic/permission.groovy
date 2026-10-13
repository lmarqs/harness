import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration

return new DeclaredRecipe(new RecipeDeclaration('agentic/permission', []), { kitchen, book, options ->
  def host = kitchen.recipe('permission')
  host.application('agentic.permissions/default')
  host
})
