package thecook.lint

import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace

/**
 * Requires braces around the bodies of `if`/`else`, `for`, `while` and `do-while` statements.
 *
 * An `if` used as a value (`val x = if (a) b else c`, an argument, a return value, an expression body, or the
 * final `if`/`else` of a lambda) is exempt.
 */
class MandatoryBracesRule :
  Rule(
    ruleId = RuleId("thecook:mandatory-braces"),
    about = About(maintainer = "The Cook"),
  ) {
  @Deprecated("Marked for removal in ktlint 2.0")
  override fun beforeVisitChildNodes(
    node: ASTNode,
    autoCorrect: Boolean,
    emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
  ) {
    when (node.elementType) {
      KtNodeTypes.IF -> {
        if (isStatement(outermostIf(node))) {
          checkBody(node.findChildByType(KtNodeTypes.THEN), "if", emit)
          node.findChildByType(KtNodeTypes.ELSE)?.let { elseBranch ->
            if (body(elseBranch)?.elementType != KtNodeTypes.IF) {
              checkBody(elseBranch, "else", emit)
            }
          }
        }
      }

      KtNodeTypes.FOR -> {
        checkBody(node.findChildByType(KtNodeTypes.BODY), "for", emit)
      }

      KtNodeTypes.WHILE -> {
        checkBody(node.findChildByType(KtNodeTypes.BODY), "while", emit)
      }

      KtNodeTypes.DO_WHILE -> {
        checkBody(node.findChildByType(KtNodeTypes.BODY), "do-while", emit)
      }
    }
  }

  private fun checkBody(
    container: ASTNode?,
    keyword: String,
    emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
  ) {
    val body = container?.let(::body) ?: return
    if (body.elementType != KtNodeTypes.BLOCK) {
      emit(body.startOffset, "Missing braces on $keyword body", false)
    } else if (startsOnStatementLine(body)) {
      emit(body.startOffset, "Body of $keyword must start on its own line", false)
    }
  }

  private fun startsOnStatementLine(block: ASTNode): Boolean {
    val firstStatement = body(block)?.treeNext ?: return false
    if (firstStatement.elementType.toString() == "RBRACE") {
      return false
    }
    return !(firstStatement.psi is PsiWhiteSpace && firstStatement.textContains('\n'))
  }

  private fun body(container: ASTNode): ASTNode? =
    generateSequence(container.firstChildNode) { it.treeNext }
      .firstOrNull { it.psi !is PsiWhiteSpace && it.psi !is PsiComment }

  private fun outermostIf(node: ASTNode): ASTNode {
    var current = node
    while (current.treeParent?.elementType == KtNodeTypes.ELSE) {
      current = current.treeParent.treeParent
    }
    return current
  }

  private fun isStatement(ifNode: ASTNode): Boolean {
    val parent = ifNode.treeParent ?: return false
    if (parent.elementType != KtNodeTypes.BLOCK) {
      return false
    }
    val isLambdaResult =
      parent.treeParent?.elementType == KtNodeTypes.FUNCTION_LITERAL && ifNode == lastStatement(parent)
    return !(isLambdaResult && ifNode.findChildByType(KtNodeTypes.ELSE) != null)
  }

  private fun lastStatement(block: ASTNode): ASTNode? =
    generateSequence(block.lastChildNode) { it.treePrev }
      .firstOrNull { it.psi !is PsiWhiteSpace && it.psi !is PsiComment && it.elementType.toString() != "RBRACE" }
}
