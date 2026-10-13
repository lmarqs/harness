package thecook.recipes.agentic

import java.nio.file.Files
import java.util.jar.JarFile
import org.junit.Test
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

class RecipePackagingTest {
  @Test void packagedDefinitionIsDiscoveredAndComposedFromItsFixture() {
    def fixture = new Properties()
    getClass().getResourceAsStream('/fixtures/definition.properties').withCloseable { fixture.load(it) }
    String name = System.getProperty('cook.recipeName')
    File artifact = new File(System.getProperty('cook.artifact'))
    String entry = "META-INF/the-cook/recipes/${name}/recipe.groovy"
    new JarFile(artifact).withCloseable { jar ->
      assert jar.getEntry(entry) != null
      assert jar.entries().toList().count { it.name == entry } == 1
      assert jar.getEntry(name.tokenize('/').last() + '.class') != null
    }
    File workspace = Files.createTempDirectory('cook-recipe-fixture-').toFile()
    try {
      def book = new Cookbook(new File(workspace, 'empty-cookbook'), [artifact])
      assert book.declaration(name).name == name
      def kitchen = new Kitchen(new File(workspace, 'repository'))
      def component = book.recipe(name, kitchen)
      assert component.definition.application == fixture.getProperty('application')
      assert component.definition.children.size() == fixture.getProperty('children').toInteger()
      component.mount()
      assert kitchen.mounted().size() == 1 + fixture.getProperty('children').toInteger()
    } finally {
      workspace.deleteDir()
    }
  }
}
