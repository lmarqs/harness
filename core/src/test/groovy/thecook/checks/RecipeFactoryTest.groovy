package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

class RecipeFactoryTest {
  @Test void repositoryRecipesLoadNativeDeclarationsWithArguments() {
    def directory = Files.createTempDirectory('cook-target-').toFile()
    try {
      def definition = new File(directory, 'book/recipes/worker/recipe.groovy')
      definition.parentFile.mkdirs()
      definition.text = '''
                return { kitchen, book, Map options ->
                    def target = kitchen.recipe('worker')
                    target.output('.worker/settings.json', [format: 'json']) {
                        key(['model']) { options.model }
                    }
                    target
                }
            '''
      def kitchen = new Kitchen(new File(directory, 'repository'))
      def target = new Cookbook(new File(directory, 'book')).recipe('worker', kitchen, [model: 'chosen'])
      assert target.definition.kind == 'recipe'
      target.mount()
      def plan = kitchen.plan()
      assert !new File(directory, 'repository/.worker/settings.json').exists()
      plan.apply()
      assert new File(directory, 'repository/.worker/settings.json').text.contains('chosen')
    } finally {
      directory.deleteDir()
    }
  }
}
