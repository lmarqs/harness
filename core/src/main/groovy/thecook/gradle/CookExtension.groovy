package thecook.gradle

import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.groovy.BatchAdapter
import thecook.groovy.RepositoryScript

class CookExtension {
  File repository
  File cookbookDirectory
  String recipe = 'development'
  File script
  Map parameters = [:]
  Iterable<File> ingredientArtifacts = []

  Kitchen kitchen() {
    def kitchen = new Kitchen(repository)
    if (script) {
      def batch = new RepositoryScript(ingredientArtifacts.toList()).evaluate(script, repository, parameters)
      BatchAdapter.mount(kitchen, batch)
      return kitchen
    }
    new Cookbook(cookbookDirectory, ingredientArtifacts.toList()).recipe(recipe, kitchen, parameters).mount()
    kitchen
  }
}
