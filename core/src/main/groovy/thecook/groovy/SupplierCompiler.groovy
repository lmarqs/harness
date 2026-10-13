package thecook.groovy

import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import org.codehaus.groovy.control.CompilerConfiguration

@CompileStatic
class SupplierCompiler {
  static CompilerConfiguration configuration(String expectedDefinitionName = null) {
    CompilerConfiguration configuration = new CompilerConfiguration()
    new GroovyShell(SupplierCompiler.classLoader, new Binding([configuration: configuration]))
    .evaluate(script(expectedDefinitionName), 'supplier-compiler.groovy')
    configuration
  }

  static String script(String expectedDefinitionName = null) {
    String source = SupplierCompiler.getResourceAsStream('/thecook/supplier-compiler.groovy').withCloseable {
      it.getText('UTF-8')
    }
    expectedDefinitionName == null ? source :
        'binding.setVariable("expectedDefinitionName", ' + JsonOutput.toJson(expectedDefinitionName) + ')\n' + source
  }
}
