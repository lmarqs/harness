import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration

return new DeclaredRecipe(new RecipeDeclaration('agentic/init', []), { kitchen, book, options ->
  def host = kitchen.recipe('init')
  host.application('agentic.init/default')
  host.includes(book.recipe('agentic/permission', kitchen))
  host.includes(book.recipe('agentic/context', kitchen))
  host
})
