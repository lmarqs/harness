package thecook.checks

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.junit.Test
import thecook.groovy.SupplierCompiler

class SupplierCompilerTest {
  @Test void matchingDefinitionCompilesWithAnIndependentInstanceName() {
    def loader = new GroovyClassLoader(getClass().classLoader, SupplierCompiler.configuration('published-name'))
    loader.parseClass(definition('Ingredient', 'published-name'))
    assert loader.loadClass('Published').newInstance().id.value == 'instance'
  }

  @Test void unspecifiedArtifactNameAllowsDifferentPublishedDefinitionsAndHelpers() {
    def loader = new GroovyClassLoader(getClass().classLoader, SupplierCompiler.configuration())
    loader.parseClass(definition('Ingredient', 'any-name'))
    loader.parseClass('class Helper { Object read() { dynamicValue() } }')
    assert loader.loadClass('Published').newInstance().id.value == 'instance'
  }

  @Test void publishedDefinitionNameMustMatchItsArtifact() {
    ['Ingredient', 'Recipe'].each { kind ->
      def configuration = new CompilerConfiguration()
      new GroovyShell(getClass().classLoader,
          new Binding([configuration: configuration, expectedDefinitionName: 'published-name']))
          .evaluate(SupplierCompiler.script(), 'supplier-compiler.groovy')
      def loader = new GroovyClassLoader(getClass().classLoader, configuration)
      try {
        loader.parseClass(definition(kind, 'different-name'))
        assert false: 'Supplier compilation accepted an annotation that differs from the artifact name'
      } catch (MultipleCompilationErrorsException expected) {
        assert expected.message.contains('published-name')
        assert expected.message.contains('different-name')
      }
    }
  }

  private static String definition(String kind, String name) {
    String contract = kind == 'Ingredient' ? 'Contributor' : 'RecipeDefinition'
    String body = kind == 'Ingredient' ? '''
            final thecook.composition.ComponentId id = thecook.composition.ComponentId.of('instance')
            List<OutputSpec> outputs() { [] }
        ''' : "Batch compose() { new Batch('instance') }"
    """
            import thecook.annotations.${kind}
            import thecook.model.*
            @${kind}('${name}')
            class Published implements ${contract} { ${body} }
        """
  }
}
