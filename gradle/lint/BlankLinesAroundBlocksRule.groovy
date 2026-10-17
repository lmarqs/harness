import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.SynchronizedStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.Violation

/**
 * A statement with a block, such as `if`, `for` or `try`, has a blank line before it unless it opens its block, and a
 * blank line after it unless it closes its block. Comments directly above the statement belong to it. Each violation's
 * line is the line a blank line goes before.
 */
class BlankLinesAroundBlocksRule extends AbstractAstVisitorRule {
  String name = 'BlankLinesAroundBlocks'
  int priority = 3
  Class astVisitorClass = BlankLinesAroundBlocksVisitor
}

class BlankLinesAroundBlocksVisitor extends AbstractAstVisitor {
  private static final List<Class> BLOCKS = [
    IfStatement,
    ForStatement,
    WhileStatement,
    DoWhileStatement,
    SwitchStatement,
    TryCatchStatement,
    SynchronizedStatement,
  ]

  @Override
  void visitBlockStatement(BlockStatement block) {
    List<Statement> statements = block.statements
    statements.eachWithIndex { Statement statement, int index ->
      if (statement.class in BLOCKS && statement.lineNumber > 0) {
        if (index > 0) {
          checkBefore(statement)
        }

        if (index < statements.size() - 1) {
          checkAfter(statement)
        }
      }
    }
    super.visitBlockStatement(block)
  }

  private void checkBefore(Statement statement) {
    def lines = sourceCode.lines
    int first = statement.lineNumber

    while (first > 1 && comment(lines[first - 2])) {
      first--
    }

    if (first > 1 && lines[first - 2].trim()) {
      report(first, 'Blank line missing before the statement')
    }
  }

  private void checkAfter(Statement statement) {
    def lines = sourceCode.lines
    int next = statement.lastLineNumber + 1

    if (next <= lines.size() && lines[next - 1].trim()) {
      report(next, 'Blank line missing after the statement')
    }
  }

  private static boolean comment(String line) {
    def text = line.trim()
    text.startsWith('//') || text.startsWith('/*') || text.startsWith('*')
  }

  private void report(int line, String message) {
    violations.add(new Violation(rule: rule, lineNumber: line, sourceLine: sourceCode.lines[line - 1].trim(), message: message))
  }
}
