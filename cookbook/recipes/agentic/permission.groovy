import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput

return new DeclaredRecipe(new RecipeDeclaration('agentic/permission', [
  new RecipeInput.RequiredPath('policy'),
  new RecipeInput.RequiredChoices('harness', ['codex', 'claude']),
  new RecipeInput.OptionalChoices('adopt', ['true', 'false'])
]), { kitchen, book, options ->
  def policyFile = new File(options.policy)
  if (!policyFile.isAbsolute()) {
    policyFile = new File(kitchen.repository.directory, policyFile.path)
  }
  if (java.nio.file.Files.isSymbolicLink(policyFile.toPath())) {
    throw new IllegalArgumentException('Policy source must be a regular file: ' + policyFile)
  }
  def policy = book.type('ingredients/agent-policy/PolicyFile.groovy').read(policyFile)
  boolean adopt = options.adopt == 'true'
  def host = kitchen.recipe('permission')
  host.application('agentic.permissions/default')
  options.harness.each { harness ->
    def renderer = book.type("recipes/agentic/${harness}/${harness.capitalize()}Files.groovy")
        .newInstance(policy, adopt)
    if (harness == 'claude') {
      renderer.instructions = null
    }
    def child = kitchen.recipe(renderer.id.value)
    child.application('agentic.permissions/' + harness)
    child.contribute { renderer }
    host.includes(child)
  }
  host
})
