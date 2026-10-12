package thecook.groovy

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilerConfiguration

@CompileStatic
class SupplierCompiler {
    static CompilerConfiguration configuration() {
        CompilerConfiguration configuration = new CompilerConfiguration()
        new GroovyShell(SupplierCompiler.classLoader, new Binding([configuration: configuration]))
            .evaluate(script(), 'supplier-compiler.groovy')
        configuration
    }

    static String script() {
        SupplierCompiler.getResourceAsStream('/thecook/supplier-compiler.groovy').withCloseable {
            it.getText('UTF-8')
        }
    }
}
