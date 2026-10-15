package thecook.cookbook

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import thecook.model.RecipeParam
import thecook.model.RecipeTexts

/**
 * Reads a recipe's texts and declarations from its source. Parsing stops before imports resolve, so a recipe whose
 * models are not on the classpath, such as one inside a jar that was not resolved, still has readable help.
 */
final class RecipeSource {
  private RecipeSource() { }

  /** The recipe's texts, or `null` when its first import carries no `@help`. */
  static RecipeTexts texts(String source, String name) {
    def unit = new CompilationUnit()
    unit.addSource(name, source)
    unit.compile(Phases.CONVERSION)
    ModuleNode module = unit.iterator().next().AST
    def texts = module.imports ? module.imports.first().annotations : []
    String help = text(texts, 'help')
    if (help == null) {
      return null
    }
    def declarations = module.statementBlock.statements
        .findAll { it instanceof ExpressionStatement && it.expression instanceof DeclarationExpression }
        .collect { (DeclarationExpression) ((ExpressionStatement) it).expression }
    def imported = module.imports.collectEntries { [it.alias, it.className] }
    new RecipeTexts(
        help, text(texts, 'confirm'), text(texts, 'yield'),
        declarations.collectMany { declaration ->
          declaration.annotations.findAll { it.classNode.name == 'param' }.collect { param(it, declaration) }
        },
        declarations.collectMany { declaration ->
          declaration.annotations.findAll { it.classNode.name == 'ingredient' }.collect { ingredient ->
            // Before imports resolve, a type reference is still a name.
            def value = ingredient.getMember('value')
            String type = value instanceof ClassExpression ? value.type.name : value.text
            imported[type] ?: type
          }
        })
  }

  private static String text(List<AnnotationNode> annotations, String name) {
    def value = annotations.find { it.classNode.name == name }?.getMember('value')
    value instanceof ConstantExpression ? ((String) value.value).strip() : null
  }

  private static RecipeParam param(AnnotationNode param, DeclarationExpression declaration) {
    def names = param.getMember('value')
    new RecipeParam(
        names instanceof ListExpression ? names.expressions*.text : [names.text],
        declaration.variableExpression.originType.toString(false),
        (String) constant(param.getMember('doc')),
        param.getMember('default')?.text,
        param.getMember('choices') instanceof ListExpression ? param.getMember('choices').expressions*.text : [],
        constant(param.getMember('many')) == true)
  }

  private static Object constant(Expression expression) {
    expression instanceof ConstantExpression ? expression.value : null
  }
}
