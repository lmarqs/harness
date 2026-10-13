import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration

return new DeclaredRecipe(new RecipeDeclaration('agentic/init', []), { kitchen, book, options ->
  def host = kitchen.recipe('init')
  host.application('agentic.init/default')
  def permissions = kitchen.recipe('permission')
  permissions.application('agentic.permissions/default')
  host.includes(permissions)
  host.includes(book.recipe('agentic/context', kitchen))
  host
})
