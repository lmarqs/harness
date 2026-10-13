package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.gradle.CookExtension

class RecipeExecutionTest {
  @Test void gradleRecipeDelegationRetainsDestinationAndResolvesSupplierAssets() {
    def root = Files.createTempDirectory('cook-recipe-execution-').toFile()
    try {
      def repository = new File(root, 'destination'); repository.mkdirs()
      new File(repository, 'input.txt').text = 'destination'
      def supplier = new File(root, 'supplier'); supplier.mkdirs()
      new File(supplier, 'input.txt').text = 'supplier'
      new File(supplier, 'asset.txt').text = 'asset'
      def child = new File(supplier, 'nested/recipe.groovy'); child.parentFile.mkdirs()
      child.text = '''
                import thecook.model.*
                import thecook.composition.ComponentId
                import thecook.filesystem.TargetPath
                def owner = ComponentId.of('example')
                def input = new File(repositoryDirectory, 'input.txt').text
                def asset = new File(scriptDirectory.parentFile, 'asset.txt').text
                def selected = preferences.message
                def contributor = [getId: { owner }, outputs: {
                    [new FileOutput(owner, TargetPath.of('result.txt'), { input + ':' + asset + ':' + selected })]
                }] as Contributor
                new Batch('example').include(contributor)
            '''
      def entry = new File(supplier, 'entry.groovy')
      entry.text = "evaluateRecipe('nested/recipe.groovy', preferences + [message: 'delegated'])"
      def extension = new CookExtension(repository: repository, script: entry, parameters: [message: 'original'])
      extension.kitchen().plan().apply()
      assert new File(repository, 'result.txt').text == 'destination:asset:delegated'
      assert !new File(supplier, 'result.txt').exists()
    } finally {
      root.deleteDir()
    }
  }
}
