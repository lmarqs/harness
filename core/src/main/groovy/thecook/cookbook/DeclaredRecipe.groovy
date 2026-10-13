package thecook.cookbook

import thecook.model.RecipeDeclaration

class DeclaredRecipe {
  final RecipeDeclaration declaration
  final Closure factory

  DeclaredRecipe(RecipeDeclaration declaration, Closure factory) {
    this.declaration = declaration
    this.factory = factory
  }
}
