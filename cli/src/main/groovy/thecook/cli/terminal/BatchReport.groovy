package thecook.cli.terminal

import thecook.cookbook.Argument
import thecook.cookbook.BatchRecord

/** What `batch list` and `batch inspect` print about the recorded batches. */
final class BatchReport {
  private BatchReport() {
  }

  /** One line per recorded batch, oldest first: its ID, command, result, and its recipes or the batch it reverted. */
  static void list(PrintWriter out, List<BatchRecord> batches) {
    if (batches.isEmpty()) {
      out.println('No batches are recorded in .cook/batches.')
      return
    }
    batches.each { batch ->
      out.println([batch.id, batch.command, batch.result, subject(batch)].findAll { it }.join('  '))
    }
  }

  /** One batch's recipes with their arguments, its files with what happened to each, their patches and any failure. */
  static void inspect(PrintWriter out, BatchRecord batch) {
    out.println("Batch ${batch.id}: ${batch.command}, ${batch.result}")
    if (batch.reverts != null) {
      out.println("Reverts ${batch.reverts}")
    }
    def recipes = batch.recipes
    if (recipes) {
      out.println()
      out.println('Recipes')
      recipes.each { recipe ->
        out.println("  ${recipe.address}")
        out.println("    ${([recipe.request.operand] + recipe.request.arguments.collect { written(it) }).join(' ')}")
      }
    }
    def files = batch.files
    out.println()
    out.println('Files')
    files.each { out.println("  ${it.action} ${it.path}") }
    files.each { file ->
      out.println()
      batch.patch(file.path).each { out.println(it) }
    }
    def failure = batch.failure
    if (failure != null) {
      out.println()
      out.println('Failure')
      failure.replaceAll(/\s+$/, '').split(/\r\n|\r|\n/, -1).each { out.println("  ${it}") }
    }
  }

  private static String subject(BatchRecord batch) {
    batch.reverts ?: batch.recipes*.address.join(', ')
  }

  private static String written(Argument argument) {
    switch (argument.form) {
      case Argument.Form.BARE:
        return "--${argument.name}"
      case Argument.Form.INLINE:
        return "--${argument.name}=${argument.value}"
      default:
        return "--${argument.name} ${argument.value}"
    }
  }
}
