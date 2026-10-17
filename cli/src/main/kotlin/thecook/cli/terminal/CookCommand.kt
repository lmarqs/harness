package thecook.cli.terminal

import picocli.CommandLine
import thecook.cookbook.Argument
import thecook.cookbook.BatchRecord
import thecook.cookbook.BatchRevert
import thecook.cookbook.CookbookApply
import thecook.cookbook.CookbookArtifact
import thecook.cookbook.CookbookRepositories
import thecook.cookbook.Coordinates
import thecook.cookbook.ExistingSeed
import thecook.cookbook.Proposal
import thecook.cookbook.RecipeRequest
import thecook.cookbook.RecipeSource
import thecook.model.RecipeTexts
import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Cook's command line. `stdin` answers the approval question; without it, writing needs `--batch-approve`. Coordinates
 * resolve from the repositories that `repositories` reads for a kitchen.
 */
class CookCommand
@JvmOverloads
constructor(
  private val stdout: PrintWriter,
  private val stderr: PrintWriter,
  private val repositories: (File) -> CookbookRepositories,
  private val stdin: BufferedReader? = null,
) {
  fun execute(args: Array<String>): Int {
    val command =
      try {
        parse(args)
      } catch (error: UsageError) {
        stderr.println(error.message)
        stderr.println("Run ./cook help for usage.")
        stderr.flush()
        return 2
      } catch (error: IllegalArgumentException) {
        stderr.println(error.message)
        stderr.flush()
        return 1
      }

    return try {
      when (command) {
        Help -> {
          stdout.print(grammar().usageMessage)
        }

        is RecipeHelp -> {
          val repositories by lazy { repositories(command.kitchen.toAbsolutePath().toFile()) }
          stdout.println(
            command.recipes.joinToString("\n\n") { target ->
              when (target) {
                is LocalRecipe -> recipeHelp(target)
                is Address -> addressHelp(target.coordinates, repositories)
              }
            },
          )
        }

        is ApplyRecipes -> {
          val proposal = CookbookApply(command.repository.toFile(), repositories(command.repository.toFile()), command.existing).propose(command.requests)
          if (!write(proposal, command.dryRun, command.batchApprove)) return 1
        }

        is RevertBatch -> {
          val proposal = BatchRevert(command.repository.toFile()).propose(command.batch)
          if (!write(proposal, false, command.batchApprove)) return 1
        }

        is ListBatches -> {
          listBatches(stdout, BatchRecord.list(command.repository.toFile()))
        }

        is InspectBatch -> {
          inspectBatch(stdout, BatchRecord.read(command.repository.toFile(), command.batch))
        }
      }
      stdout.flush()
      0
    } catch (error: Exception) {
      stderr.println(error.message ?: error.javaClass.simpleName)
      stderr.flush()
      1
    }
  }

  private fun parse(invocation: Array<String>): Command {
    if (invocation.contentEquals(arrayOf("--help"))) return Help
    if (invocation.any { it.substringBefore('=') == "--help" }) {
      throw UsageError("--help takes no other arguments; use help <recipe> to read a recipe")
    }
    val line = grammar()
    val parsed =
      try {
        line.parseArgs(*invocation)
      } catch (error: CommandLine.ParameterException) {
        throw UsageError(error.message ?: "Invalid arguments")
      }
    val root = line.getCommand<RootArgs>()
    val chosen = parsed.subcommand()
    val command = (chosen?.subcommand() ?: chosen)?.commandSpec()?.userObject()
    if (command != null && (root.dryRun || root.batchApprove || root.force || root.skip)) {
      throw UsageError("--dry-run, --batch-approve, --force and --skip go before a recipe, not before a command")
    }
    if (root.force && root.skip) throw UsageError("--force and --skip exclude each other")
    val kitchens = listOfNotNull(root.kitchen.kitchen, (command as? KitchenScoped)?.kitchen?.kitchen)
    if (kitchens.size > 1) throw UsageError("Option may only be supplied once: --kitchen")
    val kitchen = kitchen(kitchens.singleOrNull())
    return when (command) {
      null -> recipes(kitchen, root)
      is HelpArgs -> help(kitchen, command.recipes)
      is RevertArgs -> RevertBatch(kitchen.toAbsolutePath(), command.batch, command.batchApprove)
      is BatchArgs -> throw UsageError("batch takes list or inspect <batchId>")
      is BatchListArgs -> ListBatches(kitchen.toAbsolutePath())
      is BatchInspectArgs -> InspectBatch(kitchen.toAbsolutePath(), command.batch)
      else -> throw IllegalStateException("Unhandled command: $command")
    }
  }

  private fun recipes(
    kitchen: Path,
    root: RootArgs,
  ): Command {
    val first = root.operands.firstOrNull() ?: throw UsageError("A recipe is required")
    if (!operand(first)) throw UsageError("Not a recipe: $first. A recipe is coordinates or a .groovy path")
    val recipes = mutableListOf<Pair<String, MutableList<Argument>>>()
    var bare: String? = null
    for (token in root.operands) {
      if (token.startsWith("-")) {
        val argument = recipeArgument(token)
        recipes.last().second.add(argument)
        bare = argument.name.takeIf { argument.form == Argument.Form.BARE }
      } else if (operand(token)) {
        recipes.add(recipeOperand(token, bare) to mutableListOf())
        bare = null
      } else {
        val name = bare ?: throw UsageError("Not a recipe: $token. Recipe arguments start with --")
        recipes.last().second.apply { set(lastIndex, Argument.separate(name, token)) }
        bare = null
      }
    }
    val requests = recipes.map { (operand, arguments) -> RecipeRequest(operand, arguments) }
    val existing =
      when {
        root.force -> ExistingSeed.REPLACE
        root.skip -> ExistingSeed.KEEP
        else -> ExistingSeed.REFUSE
      }
    return ApplyRecipes(kitchen.toAbsolutePath(), requests, root.dryRun, root.batchApprove, existing)
  }

  private fun operand(token: String): Boolean = '/' in token || ':' in token || token.endsWith(".groovy")

  private fun recipeOperand(
    token: String,
    bare: String?,
  ): String {
    if (!Coordinates.written(token)) return token
    try {
      Coordinates.parse(token)
    } catch (error: IllegalArgumentException) {
      if (bare != null) throw UsageError("Not a recipe: $token. To give it to --$bare, write --$bare=$token")
      throw error
    }
    return token
  }

  private fun recipeArgument(token: String): Argument {
    val name = token.substringBefore('=')
    if (name in BEFORE_RECIPE) throw UsageError("$name goes before the first recipe")
    if (!name.matches(Regex("--[a-z][a-z0-9-]*"))) throw UsageError("Unknown option for the recipe: $token")
    if ('=' !in token) return Argument.bare(name.removePrefix("--"))
    val value = token.substringAfter('=')
    if (value.isEmpty()) throw UsageError("Missing value for $name")
    return Argument.inline(name.removePrefix("--"), value)
  }

  private fun help(
    kitchen: Path,
    operands: List<String>,
  ): Command {
    if (operands.isEmpty()) return Help
    return RecipeHelp(
      kitchen,
      operands.map { operand ->
        if (operand.startsWith("-")) throw UsageError("help takes recipes only: $operand")
        if (Coordinates.written(operand)) {
          Address(Coordinates.parse(operand))
        } else {
          if (!operand(operand)) throw UsageError("Not a recipe: $operand. A recipe is coordinates or a .groovy path")
          val script = path(kitchen, operand, "recipe")
          if (!Files.isRegularFile(script)) throw IllegalArgumentException("Recipe file not found: $operand")
          LocalRecipe(script, operand)
        }
      },
    )
  }

  private fun addressHelp(
    coordinates: Coordinates,
    repositories: CookbookRepositories,
  ): String {
    val artifact = repositories.artifact(coordinates)
    val recipe = coordinates.recipe ?: return frontPage(artifact)
    val texts =
      RecipeSource.texts(artifact.recipeSource(recipe), "$recipe.groovy")
        ?: throw IllegalArgumentException("$coordinates has no help: its first import carries no @help")
    return render(coordinates.toString(), texts)
  }

  private fun frontPage(artifact: CookbookArtifact): String {
    val coordinates = artifact.coordinates
    return listOfNotNull(
      coordinates.artifactAddress,
      artifact.readme?.trim(),
      artifact.recipes.takeIf { it.isNotEmpty() }?.let { recipes ->
        "Recipes\n${recipes.joinToString("\n") { "  ${coordinates.recipeAddress(it)}" }}"
      },
      artifact.models.takeIf { it.isNotEmpty() }?.let { models ->
        "Models\n${models.joinToString("\n") { "  ${coordinates.group}.${coordinates.artifact}.$it" }}"
      },
    ).joinToString("\n\n")
  }

  private fun recipeHelp(command: LocalRecipe): String {
    val texts =
      RecipeSource.texts(Files.readString(command.script), command.script.fileName.toString())
        ?: throw IllegalArgumentException("${command.operand} has no help: its first import carries no @help")
    return render(command.operand, texts)
  }

  private fun render(
    name: String,
    texts: RecipeTexts,
  ): String {
    val params = texts.params.joinToString("\n") { param ->
      val facts = listOfNotNull(
        param.default?.let { "default: $it" } ?: "required",
        param.choices.takeIf { it.isNotEmpty() }?.let { "choices: ${it.joinToString(", ")}" },
        "repeatable".takeIf { param.many },
      )
      "  ${param.names.joinToString(", ") { "--$it=<${param.type}>" }}\n      ${param.doc ?: ""}; ${facts.joinToString("; ")}"
    }
    return listOfNotNull(
      name,
      texts.help,
      params.takeIf { it.isNotEmpty() }?.let { "Parameters\n$it" },
      texts.ingredients.takeIf { it.isNotEmpty() }?.let { "Ingredients\n${it.joinToString("\n") { type -> "  $type" }}" },
      texts.confirm?.let { "Confirm\n${indent(it)}" },
      texts.yieldText?.let { "After applying\n${indent(it)}" },
    ).joinToString("\n\n")
  }

  // Reviews the proposal, asks unless approved in advance, writes it and reports each path; false when declined.
  private fun write(
    proposal: Proposal,
    dryRun: Boolean,
    batchApprove: Boolean,
  ): Boolean {
    val actions = proposal.actions
    if (actions.isEmpty()) {
      stdout.println("Already cooked.")
      return true
    }
    review(proposal, proposal.changes)
    if (!dryRun && !batchApprove && !approved()) {
      stderr.println("Nothing written.")
      stderr.flush()
      return false
    }
    if (!dryRun) proposal.commit()
    actions.forEach { (path, action) -> stdout.println("${status(action, dryRun)} $path") }
    if (!dryRun) {
      proposal.recipes.filter { it.yieldText != null }.forEach {
        stdout.println()
        stdout.println("After applying ${it.address}")
        stdout.println(indent(it.yieldText))
      }
    }
    return true
  }

  // Each release whose sources changed under a recorded version, each recipe's consequence the diff cannot show, then
  // the diff itself: what one approval covers.
  private fun review(
    proposal: Proposal,
    paths: Collection<String>,
  ) {
    proposal.changedSources.forEach {
      stdout.println("Changed source ${it.address}")
      stdout.println("  cook.lock recorded sha256 ${it.recorded}; the release now has sha256 ${it.current}")
      stdout.println("  its sources changed under the same version; review the diff before approving")
      stdout.println()
    }
    proposal.recipes.filter { it.confirm != null }.forEach {
      stdout.println("Confirm ${it.address}")
      stdout.println(indent(it.confirm))
      stdout.println()
    }
    paths.forEach { path -> proposal.patch(path).forEach(stdout::println) }
    stdout.println()
    stdout.flush()
  }

  private fun approved(): Boolean {
    val input =
      stdin ?: throw IllegalArgumentException(
        "Cook asks before writing, and there is no terminal to ask in; run with --batch-approve to write without asking",
      )
    stdout.print("Apply these changes? [y/N] ")
    stdout.flush()
    return input.readLine()?.trim()?.lowercase() in setOf("y", "yes")
  }

  private fun status(
    action: String,
    dryRun: Boolean,
  ): String = if (dryRun) "Would $action" else STATUS.getValue(action)

  private fun indent(text: String): String = text.lines().joinToString("\n") { if (it.isEmpty()) it else "  $it" }

  private fun path(
    kitchen: Path,
    value: String,
    option: String,
  ): Path =
    try {
      kitchen.resolve(value)
    } catch (error: InvalidPathException) {
      throw UsageError("Invalid path for $option: ${error.reason}")
    }

  private fun kitchen(value: String?): Path {
    if (value == null) return Path.of("")
    if (value.isEmpty()) throw UsageError("Missing value for --kitchen")
    val directory = path(Path.of(""), value, "--kitchen")
    if (!Files.isDirectory(directory)) throw IllegalArgumentException("Kitchen directory not found: $value")
    return directory
  }

  private sealed interface Command

  private data object Help : Command

  private data class RecipeHelp(val kitchen: Path, val recipes: List<HelpTarget>) : Command

  private sealed interface HelpTarget

  private data class LocalRecipe(val script: Path, val operand: String) : HelpTarget

  private data class Address(val coordinates: Coordinates) : HelpTarget

  private data class ApplyRecipes(
    val repository: Path,
    val requests: List<RecipeRequest>,
    val dryRun: Boolean,
    val batchApprove: Boolean,
    val existing: ExistingSeed,
  ) : Command

  private data class RevertBatch(
    val repository: Path,
    val batch: String,
    val batchApprove: Boolean,
  ) : Command

  private data class ListBatches(
    val repository: Path,
  ) : Command

  private data class InspectBatch(
    val repository: Path,
    val batch: String,
  ) : Command

  private class UsageError(
    message: String,
  ) : IllegalArgumentException(message)

  private companion object {
    val BEFORE_RECIPE = setOf("-C", "--kitchen", "--dry-run", "--batch-approve", "--force", "--skip")
    val STATUS =
      mapOf("create" to "Created", "update" to "Updated", "delete" to "Deleted", "replace" to "Replaced", "keep" to "Kept")
  }
}
