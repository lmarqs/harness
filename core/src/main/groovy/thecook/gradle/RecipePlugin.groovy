package thecook.gradle

class RecipePlugin extends IngredientPlugin {
  @Override protected String getDefinitionsDirectory() {
    'recipes'
  }

  @Override void apply(org.gradle.api.Project project) {
    super.apply(project)
    def flatSource = project.providers.gradleProperty('flatRecipeSource')
    if (flatSource.isPresent()) {
      def source = project.file(flatSource.get())
      def path = project.providers.gradleProperty('cookbookPath').get()
      def sets = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer)
      sets.main.groovy.srcDir(source.parentFile)
      sets.main.groovy.include(source.name)
      project.tasks.named('processResources') {
        from(source) {
          rename { 'recipe.groovy' }
          into("META-INF/the-cook/recipes/${path}")
        }
      }
    }
  }
}
