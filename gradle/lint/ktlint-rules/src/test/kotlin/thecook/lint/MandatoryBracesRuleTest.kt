package thecook.lint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class MandatoryBracesRuleTest {
  private val assertThatCode = assertThatRule { MandatoryBracesRule() }

  @Test
  fun `flags an if statement without braces`() {
    val code =
      """
      fun f(a: Boolean) {
          if (a) return
      }
      """.trimIndent()
    assertThatCode(code).hasLintViolationWithoutAutoCorrect(2, 12, "Missing braces on if body")
  }

  @Test
  fun `flags an else branch without braces`() {
    val code =
      """
      fun f(a: Boolean) {
          if (a) {
              println(1)
          } else println(2)
      }
      """.trimIndent()
    assertThatCode(code).hasLintViolationWithoutAutoCorrect(4, 12, "Missing braces on else body")
  }

  @Test
  fun `accepts an else-if chain with braced bodies`() {
    val code =
      """
      fun f(a: Boolean, b: Boolean) {
          if (a) {
              println(1)
          } else if (b) {
              println(2)
          } else {
              println(3)
          }
      }
      """.trimIndent()
    assertThatCode(code).hasNoLintViolations()
  }

  @Test
  fun `flags for, while and do-while loops without braces`() {
    val code =
      """
      fun f(items: List<Int>) {
          for (item in items) println(item)
          while (items.isEmpty()) println(0)
          do println(1) while (items.isEmpty())
      }
      """.trimIndent()
    assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
      LintViolation(2, 25, "Missing braces on for body"),
      LintViolation(3, 29, "Missing braces on while body"),
      LintViolation(4, 8, "Missing braces on do-while body"),
    )
  }

  @Test
  fun `exempts if used as an expression`() {
    val code =
      """
      fun f(a: Boolean): Int {
          val x = if (a) 1 else 2
          println(if (a) "yes" else "no")
          listOf(1).map { if (it > 0) it else -it }
          return if (a) x else 0
      }

      fun g(a: Boolean) = if (a) 1 else 2

      fun h(a: Boolean): Int {
          return when {
              a -> if (a) 1 else 2
              else -> 0
          }
      }
      """.trimIndent()
    assertThatCode(code).hasNoLintViolations()
  }

  @Test
  fun `flags an if without else as the last statement of a lambda`() {
    val code =
      """
      fun f(items: List<Int>) {
          items.forEach { if (it > 0) println(it) }
      }
      """.trimIndent()
    assertThatCode(code).hasLintViolationWithoutAutoCorrect(2, 33, "Missing braces on if body")
  }

  @Test
  fun `flags braced bodies written on the statement line`() {
    val code =
      """
      fun f(a: Boolean, items: List<Int>) {
          if (a) { println(1) }
          for (item in items) { println(item) }
      }
      """.trimIndent()
    assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
      LintViolation(2, 12, "Body of if must start on its own line"),
      LintViolation(3, 25, "Body of for must start on its own line"),
    )
  }
}
