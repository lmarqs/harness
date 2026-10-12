package thecook.gradle

class RecipePlugin extends IngredientPlugin {
    @Override protected String getDefinitionsDirectory() { 'recipes' }
}
