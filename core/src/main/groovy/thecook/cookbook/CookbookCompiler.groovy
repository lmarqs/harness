package thecook.cookbook

import groovy.transform.CompileStatic
import groovy.transform.Field
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * Compiles cookbook units from their sources. Every unit gets the author's annotations, `groovy.json`, `groovy.yaml`,
 * `groovy.toml` and `java.nio.file` without imports, the annotations first so `Files` and `File` name them.
 */
final class CookbookCompiler {
  private static final List<String> DEFAULT_IMPORTS =
  ['thecook.cookbook.api', 'groovy.json', 'groovy.yaml', 'groovy.toml', 'java.nio.file']

  private CookbookCompiler() { }

  /**
   * Compiles the artifact's models in one unit, since one model file may declare several types, and defines them in
   * a class loader of their own.
   */
  static ClassLoader models(CookbookArtifact artifact) {
    def config = configuration()
    config.addCompilationCustomizers(new ModelSuperclass())
    ClassLoader engine = CookbookCompiler.classLoader
    def unit = new CompilationUnit(config, null, new GroovyClassLoader(engine, config))
    artifact.models.each { unit.addSource("${it}.groovy", artifact.modelSource(it)) }
    unit.compile(Phases.CLASS_GENERATION)
    new DefinedClasses(unit.classes.collectEntries { [(it.name): it.bytes] }, engine)
  }

  /** Compiles a recipe in its own class loader over `models`; its `@param` and `@ingredient` lines become fields. */
  static CompiledRecipe recipe(String source, String name, ClassLoader models) {
    def config = configuration()
    def fields = new RecipeFields()
    config.addCompilationCustomizers(fields)
    def loader = new GroovyClassLoader(models, config)
    def file = name.replaceAll(/[^A-Za-z0-9_]/, '_') + '.groovy'
    Class type = loader.parseClass(new GroovyCodeSource(source, file, GroovyShell.DEFAULT_CODE_BASE))
    new CompiledRecipe(type, fields.names, fields.defaulted)
  }

  private static CompilerConfiguration configuration() {
    def config = new CompilerConfiguration()
    config.addCompilationCustomizers(new ImportCustomizer().addStarImports(DEFAULT_IMPORTS as String[]))
    config
  }

  /** Gives each `@Ingredient` class without a superclass the engine's `CookbookModel`. */
  private static final class ModelSuperclass extends CompilationCustomizer {
    ModelSuperclass() {
      super(CompilePhase.CONVERSION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode type) {
      boolean model = type.annotations.any { it.classNode.nameWithoutPackage == 'Ingredient' }

      if (model && type.superClass == ClassHelper.OBJECT_TYPE) {
        type.superClass = ClassHelper.make(CookbookModel)
      }
    }
  }

  /** Turns a recipe's annotated declarations into script fields, which keep their annotations at runtime. */
  private static final class RecipeFields extends CompilationCustomizer {
    final List<String> names = []
    final Set<String> defaulted = []

    RecipeFields() {
      super(CompilePhase.CONVERSION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode type) {
      if (!type.script) {
        return
      }

      source.AST.statementBlock.statements.each { statement ->
        if (statement instanceof ExpressionStatement && statement.expression instanceof DeclarationExpression) {
          def declaration = (DeclarationExpression) statement.expression

          if (declaration.annotations.any { it.classNode.nameWithoutPackage in ['param', 'ingredient'] }) {
            declaration.addAnnotation(new AnnotationNode(ClassHelper.make(Field)))
            names.add(declaration.variableExpression.name)

            // Annotation reflection cannot tell `default = []` from no default, and only the latter is required.
            if (declaration.annotations.any {
                  it.classNode.nameWithoutPackage == 'param' && it.getMember('default') != null
                }) {
              defaulted.add(declaration.variableExpression.name)
            }
          }
        }
      }
    }
  }

  @CompileStatic
  private static final class DefinedClasses extends ClassLoader {
    private final Map<String, byte[]> classes

    DefinedClasses(Map<String, byte[]> classes, ClassLoader parent) {
      super(parent)
      this.classes = classes
    }

    @Override
    protected Class<?> findClass(String name) {
      byte[] bytes = classes[name]

      if (bytes == null) {
        throw new ClassNotFoundException(name)
      }

      defineClass(name, bytes, 0, bytes.length)
    }
  }
}

/**
 * A compiled recipe: its script class, the names of its `@param` and `@ingredient` fields in source order, and the
 * params that declare a default.
 */
final class CompiledRecipe {
  final Class type
  final List<String> fields
  final Set<String> defaulted

  CompiledRecipe(Class type, List<String> fields, Set<String> defaulted) {
    this.type = type
    this.fields = fields
    this.defaulted = defaulted
  }

  /** Whether the param in the field `name` declares a default, so the recipe runs without it. */
  boolean hasDefault(String name) {
    defaulted.contains(name)
  }
}
