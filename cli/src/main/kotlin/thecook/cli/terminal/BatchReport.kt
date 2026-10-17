package thecook.cli.terminal

import thecook.cookbook.Argument
import thecook.cookbook.RecordedBatch
import java.io.PrintWriter

/** One line per recorded batch, oldest first: its ID, command, result, and its recipes or the batch it reverted. */
internal fun listBatches(
  out: PrintWriter,
  batches: List<RecordedBatch>,
) {
  if (batches.isEmpty()) {
    out.println("No batches are recorded in .cook/batches.")
    return
  }
  batches.forEach { batch ->
    out.println(
      listOf(batch.id, batch.command, batch.result, subject(batch)).filter { it.isNotEmpty() }.joinToString("  "),
    )
  }
}

/** One batch's recipes with their arguments, its files with what happened to each, their patches and any failure. */
internal fun inspectBatch(
  out: PrintWriter,
  batch: RecordedBatch,
) {
  out.println("Batch ${batch.id}: ${batch.command}, ${batch.result}")
  batch.reverts?.let { out.println("Reverts $it") }
  if (batch.recipes.isNotEmpty()) {
    out.println()
    out.println("Recipes")
    batch.recipes.forEach { recipe ->
      out.println("  ${recipe.address}")
      val written = listOf(recipe.request.operand) + recipe.request.arguments.map(::written)
      out.println("    ${written.joinToString(" ")}")
    }
  }
  out.println()
  out.println("Files")
  batch.files.forEach { out.println("  ${it.action} ${it.path}") }
  batch.files.forEach { file ->
    out.println()
    batch.patch(file.path).forEach(out::println)
  }
  batch.failure?.let {
    out.println()
    out.println("Failure")
    it.trimEnd().lines().forEach { line -> out.println("  $line") }
  }
}

private fun subject(batch: RecordedBatch): String = batch.reverts ?: batch.recipes.joinToString(", ") { it.address }

private fun written(argument: Argument): String =
  when (argument.form) {
    Argument.Form.BARE -> "--${argument.name}"
    Argument.Form.INLINE -> "--${argument.name}=${argument.value}"
    else -> "--${argument.name} ${argument.value}"
  }
