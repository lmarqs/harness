import fixture.Declaration
import thecook.cookbook.DeclaredRecipe

return new DeclaredRecipe(Declaration.describe(), { kitchen, book, options ->
  throw new AssertionError('factory should not run while reading metadata')
})
