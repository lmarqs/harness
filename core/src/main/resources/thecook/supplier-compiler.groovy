import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.control.CompilationUnit
import groovy.transform.CompilationUnitAware

// Published definitions always compile statically. Unannotated legacy factories
// remain readable during migration; repository scripts use a separate compiler.
class PublishedDefinitions extends CompilationCustomizer implements CompilationUnitAware {
    CompilationUnit compilationUnit
    private final String expectedDefinitionName
    PublishedDefinitions(String expectedDefinitionName) {
        super(CompilePhase.CANONICALIZATION)
        this.expectedDefinitionName = expectedDefinitionName
    }
    void call(org.codehaus.groovy.control.SourceUnit source,
              org.codehaus.groovy.classgen.GeneratorContext context,
              org.codehaus.groovy.ast.ClassNode node) {
        def contracts = ['thecook.annotations.Ingredient': 'thecook.model.Contributor',
                         'thecook.annotations.Recipe': 'thecook.model.RecipeDefinition']
        def published = node.annotations.findAll { contracts.containsKey(it.classNode.name) }
        if (!published) return
        if (published.size() != 1) {
            source.addError(new SyntaxException('A published definition has one kind', node.lineNumber, node.columnNumber))
            return
        }
        def annotation = published.first()
        def definitionName = annotation.getMember('value')?.text
        if (expectedDefinitionName != null && definitionName != expectedDefinitionName) {
            source.addError(new SyntaxException("Published definition '${definitionName}' must match artifact name '${expectedDefinitionName}'", node.lineNumber, node.columnNumber))
            return
        }
        def contract = contracts[annotation.classNode.name]
        if (!node.implementsInterface(ClassHelper.makeWithoutCaching(contract))) {
            source.addError(new SyntaxException("A published definition must implement ${contract}", node.lineNumber, node.columnNumber))
            return
        }
        def unchecked = ([node] + node.methods + node.declaredConstructors).any { member ->
            member.annotations.any {
                it.classNode.name == 'groovy.transform.CompileDynamic' ||
                (it.classNode.name in ['groovy.transform.CompileStatic', 'groovy.transform.TypeChecked'] &&
                 it.getMember('value')?.text?.contains('SKIP'))
            }
        }
        if (unchecked) {
            source.addError(new SyntaxException('Published definitions require static checking', node.lineNumber, node.columnNumber))
            return
        }
        // Static compilation of a subclass does not check inherited method bodies.
        // JVM implementations from Java/Kotlin are already compiled; Groovy bodies
        // must carry evidence that the supplier compiler checks their implementation.
        def unsafeInherited = ClassHelper.make(source.classLoader.loadClass(contract)).methods.find { required ->
            def implementation = node.getMethod(required.name, required.parameters)
            if (!implementation || implementation.abstract || implementation.declaringClass == node) return false
            def declaring = implementation.declaringClass
            def groovyBody = declaring.primaryClassNode || declaring.implementsInterface(ClassHelper.make(GroovyObject))
            if (!groovyBody) return false
            def annotations = implementation.annotations + declaring.annotations
            def disabled = annotations.any {
                it.classNode.name == 'groovy.transform.CompileDynamic' ||
                (it.classNode.name in ['groovy.transform.CompileStatic', 'groovy.transform.TypeChecked'] &&
                 it.getMember('value')?.text?.contains('SKIP'))
            }
            def checked = annotations.any {
                it.classNode.name == 'groovy.transform.CompileStatic' || contracts.containsKey(it.classNode.name)
            }
            disabled || !checked
        }
        if (unsafeInherited) {
            source.addError(new SyntaxException("Published definitions require a statically checked inherited implementation of ${unsafeInherited.name}", node.lineNumber, node.columnNumber))
            return
        }
        def transform = new ASTTransformationCustomizer(CompileStatic)
        transform.setCompilationUnit(compilationUnit)
        transform.call(source, context, node)
    }
}
configuration.addCompilationCustomizers(new PublishedDefinitions(
    binding.hasVariable('expectedDefinitionName') ? binding.getVariable('expectedDefinitionName') : null))
