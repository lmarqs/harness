import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule

/**
 * A chain of up to two method calls may stay on one line; a longer chain puts each call on its own line. The receiver
 * stays on the line of the first call.
 */
class ChainedCallsRule extends AbstractAstVisitorRule {
  String name = 'ChainedCalls'
  int priority = 3
  Class astVisitorClass = ChainedCallsVisitor
}

class ChainedCallsVisitor extends AbstractAstVisitor {
  private static final int MAX_INLINE = 2
  private final Set<MethodCallExpression> checked = Collections.newSetFromMap(new IdentityHashMap())

  @Override
  void visitMethodCallExpression(MethodCallExpression call) {
    if (!checked.contains(call)) {
      check(chain(call))
    }

    super.visitMethodCallExpression(call)
  }

  // The calls of the chain that `call` ends, innermost first.
  private List<MethodCallExpression> chain(MethodCallExpression call) {
    List<MethodCallExpression> calls = []
    Expression current = call

    while (current instanceof MethodCallExpression) {
      calls.add(0, (MethodCallExpression) current)
      current = ((MethodCallExpression) current).objectExpression
    }

    checked.addAll(calls)
    calls
  }

  private void check(List<MethodCallExpression> calls) {
    if (calls.size() <= MAX_INLINE) {
      return
    }

    for (int index = 1; index < calls.size(); index++) {
      def previous = calls[index - 1]
      def method = calls[index].method

      if (method.lineNumber > 0 && method.lineNumber <= previous.lastLineNumber) {
        addViolation(method, "A chain of ${calls.size()} calls puts each call on its own line")
      }
    }
  }
}
