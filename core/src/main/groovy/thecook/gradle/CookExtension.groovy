package thecook.gradle

import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.rendering.RenderTree
import thecook.reconciliation.Reconciler
import thecook.filesystem.Repository
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
    new Cookbook(cookbookDirectory, ingredientArtifacts.toList()).recipe(recipe, kitchen, parameters).mount()
    kitchen
  }
  RenderTree render() {
    if (!script) return kitchen().render()
    new RepositoryScript(ingredientArtifacts.toList()).evaluate(script, repository, parameters)
  }

  def plan() {
    new Reconciler().plan(new Repository(repository), render())
  }
}
