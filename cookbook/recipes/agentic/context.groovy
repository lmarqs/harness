import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput
import thecook.model.Provider

return new DeclaredRecipe(new RecipeDeclaration('agentic/context', [
  new RecipeInput.RequiredChoices('harness', ['codex', 'claude']),
  new RecipeInput.OptionalChoices('adopt', ['true', 'false'])
]), { kitchen, book, options ->
  def host = kitchen.recipe('context')
  host.application('agentic.context/default')
  host.output('AGENTS.md', [seed: true]) {
    text {
      '# Agent instructions\n'
    }
  }
  options.harness.each { harness ->
    def child = kitchen.recipe(harness + '-context')
    child.application('agentic.context/' + harness)
    if (harness == 'claude') {
      def renderer = book.type('recipes/agentic/claude/ClaudeFiles.groovy').newInstance(child.id.value)
      renderer.instructions = { ['@AGENTS.md'] } as Provider
      renderer.adoptInstructions = options.adopt == 'true'
      child.contribute { renderer }
    }
    host.includes(child)
  }
  host
})
