package thecook.cli.terminal

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import picocli.CommandLine
import thecook.application.CookService
import thecook.cookbook.Cookbook
import thecook.cookbook.CookbookApply
import thecook.cookbook.CookbookArtifact
import thecook.cookbook.CookbookRepositories
import thecook.cookbook.Coordinates
import thecook.cookbook.ExistingSeed
import thecook.cookbook.Proposal
import thecook.cookbook.RecipeRequest
import thecook.cookbook.RecipeSource
import thecook.model.RecipeInput
import thecook.model.RecipeArguments
import thecook.model.RecipeDeclaration
import thecook.model.RecipeTexts
import java.io.BufferedReader
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Cook's command line. `stdin` answers the approval question; without it, writing needs `--batch-approve`. */
class CookCommand
@JvmOverloads
constructor(
  private val service: CookService,
  private val stdout: PrintWriter,
  private val stderr: PrintWriter,
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
          val repositories by lazy { CookbookRepositories.at(command.kitchen.toAbsolutePath().toFile()) }
          stdout.println(
            command.recipes.joinToString("\n\n") { target ->
              when (target) {
                is LocalRecipe -> recipeHelp(target)
                is Address -> addressHelp(target.coordinates, repositories)
              }
            },
          )
        }

        is Init -> {
          stdout.println("Ready: ${service.initialize(command.repository)}")
        }

        is Cook -> {
          val changes =
            if (command.remove) {
              service.remove(command.repository, command.removalApplications, command.dryRun)
            } else {
              service.cook(command.repository, command.script, command.libraries, command.preferences, command.dryRun)
            }
          if (changes.isEmpty()) stdout.println(if (command.remove) "Already removed." else "Already cooked.")
          val verb = if (command.remove) {
            if (command.dryRun) "Would remove" else "Removed"
          } else if (command.dryRun) "Would cook" else "Cooked"
          changes.forEach { stdout.println("$verb $it") }
        }

        is CookCoordinates -> {
          val proposal = CookbookApply(command.repository.toFile(), command.existing).propose(command.requests)
          val actions = proposal.actions
          if (actions.isEmpty()) {
            stdout.println("Already cooked.")
          } else {
            review(proposal, proposal.changes)
            if (!command.dryRun && !command.batchApprove && !approved()) {
              stderr.println("Nothing written.")
              stderr.flush()
              return 1
            }
            if (!command.dryRun) proposal.commit()
            actions.forEach { (path, action) -> stdout.println("${status(action, command.dryRun)} $path") }
            if (!command.dryRun) {
              proposal.recipes.filter { it.yieldText != null }.forEach {
                stdout.println()
                stdout.println("After applying ${it.address}")
                stdout.println(indent(it.yieldText))
              }
            }
          }
        }

        is Plan -> {
          val plan = service.plan(command.repository, command.script, command.libraries, command.preferences)
          plan.save(command.savedPlan)
          plan.changes.forEach(stdout::println)
          stdout.println("Saved plan to ${command.savedPlan}")
        }

        is Apply -> {
          service.apply(command.repository, command.savedPlan)
          stdout.println("Applied plan from ${command.savedPlan}")
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
    val command = parsed.subcommand()?.commandSpec()?.userObject()
    if (command != null && (root.dryRun || root.batchApprove || root.force || root.skip || root.remove || root.libraries.isNotEmpty())) {
      throw UsageError("--dry-run, --batch-approve, --force, --skip, --remove and --library go before a recipe, not before a command")
    }
    if (root.force && root.skip) throw UsageError("--force and --skip exclude each other")
    val kitchens = listOfNotNull(root.kitchen.kitchen, command?.let(::kitchenOf))
    if (kitchens.size > 1) throw UsageError("Option may only be supplied once: --kitchen")
    val kitchen = kitchen(kitchens.singleOrNull())
    val repository = kitchen.toAbsolutePath()
    return when (command) {
      null -> recipe(kitchen, root)
      is HelpArgs -> help(kitchen, command.recipes)
      is InitArgs ->
        if (command.remove) {
          val recipe = repository.resolve("cookbook/recipes/init/recipe.groovy")
          Cook(repository, recipe, emptyList(), emptyMap(), false, true, RecipeArguments.removal(declaration(recipe), emptyMap()))
        } else {
          Init(repository)
        }
      is CookArgs -> {
        val script = command.script?.let { path(kitchen, it, "--script") } ?: repository.resolve("cook.groovy")
        val preferences = sets(command.sets)
        val removal = if (command.remove) RecipeArguments.removal(declaration(script), preferences) else emptySet()
        Cook(repository, script, libraries(kitchen, command.libraries), single(preferences), command.dryRun, command.remove, removal)
      }
      is PlanArgs ->
        Plan(
          repository,
          command.script?.let { path(kitchen, it, "--script") } ?: repository.resolve("cook.groovy"),
          command.plan?.let { path(kitchen, it, "--plan") } ?: repository.resolve(".cook/plan.json"),
          libraries(kitchen, command.libraries),
          single(sets(command.sets)),
        )
      is ApplyArgs -> Apply(repository, command.plan?.let { path(kitchen, it, "--plan") } ?: repository.resolve(".cook/plan.json"))
      else -> throw IllegalStateException("Unhandled command: $command")
    }
  }

  private fun recipe(
    kitchen: Path,
    root: RootArgs,
  ): Command {
    val entrypoint = root.operands.firstOrNull() ?: throw UsageError("A command or recipe is required")
    val localScript = entrypoint.endsWith(".groovy") && !entrypoint.contains("://")
    if (!localScript && Coordinates.written(entrypoint)) return coordinates(kitchen, root)
    if (root.force || root.skip) throw UsageError("--force and --skip apply to recipe coordinates")
    val repository = kitchen.toAbsolutePath()
    val recipe =
      if (localScript) {
        path(kitchen, entrypoint, "recipe").also {
          if (!Files.isRegularFile(it)) throw IllegalArgumentException("Recipe file not found: $entrypoint")
        }
      } else {
        if (!entrypoint.matches(RECIPE_NAME)) throw UsageError("Invalid recipe name: $entrypoint")
        findRecipe(repository, entrypoint)
      }
    val arguments = recipeArguments(root.operands.drop(1))
    if (!localScript) {
      val declared = try {
        declaration(recipe)
      } catch (error: IllegalArgumentException) {
        if (error.message?.startsWith("Recipe has no argument declaration:") != true &&
          error.message?.startsWith("Cookbook source not found:") != true
        ) throw error
        null
      }
      if (declared != null && declared.name != entrypoint) {
        throw IllegalArgumentException("Recipe identity does not match: $entrypoint")
      }
    }
    val removal = if (root.remove) RecipeArguments.removal(declaration(recipe), arguments) else emptySet()
    return Cook(repository, recipe, libraries(kitchen, root.libraries), arguments, root.dryRun, root.remove, removal)
  }

  // Values are written only as --name=value, so a word without -- always starts the next recipe; a bare --name carries
  // no value, and the recipe reads it as true for a yes-or-no param.
  private fun coordinates(
    kitchen: Path,
    root: RootArgs,
  ): Command {
    if (root.remove || root.libraries.isNotEmpty()) {
      throw UsageError("--remove and --library do not apply to recipe coordinates")
    }
    // Checked in reading order, so `--name value` reports the missing `=` before `value` reads as a recipe.
    val recipes = mutableListOf<Pair<String, MutableMap<String, MutableList<String?>>>>()
    var bare: String? = null
    for (token in root.operands) {
      if (token.startsWith("-")) {
        val (name, value) = coordinatesArgument(token)
        recipes.last().second.getOrPut(name) { mutableListOf() }.add(value)
        bare = if (value == null) "--$name" else null
      } else {
        recipes.add(coordinatesOperand(token, bare) to linkedMapOf())
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
    return CookCoordinates(kitchen.toAbsolutePath(), requests, root.dryRun, root.batchApprove, existing)
  }

  private fun coordinatesOperand(
    token: String,
    bare: String?,
  ): String {
    if (token.endsWith(".groovy")) {
      throw UsageError(
        "Not supported yet: the path $token together with recipe coordinates; apply it in its own invocation",
      )
    }
    if (!Coordinates.written(token)) {
      if (bare != null) throw UsageError("Not a recipe: $token. To give it to $bare, write $bare=$token")
      throw UsageError("Not a recipe: $token. Recipe arguments start with --")
    }
    return token
  }

  private fun coordinatesArgument(token: String): Pair<String, String?> {
    val name = token.substringBefore('=')
    if (name in BEFORE_RECIPE) throw UsageError("$name goes before the first recipe")
    if (!name.matches(Regex("--[a-z][a-z0-9-]*")) || name in RESERVED) {
      throw UsageError("Unknown option for the recipe: $token")
    }
    if ('=' !in token) return name.removePrefix("--") to null
    val value = token.substringAfter('=')
    if (value.isEmpty()) throw UsageError("Missing value for $name")
    return name.removePrefix("--") to value
  }

  // A recipe's arguments are its own; Cook only splits them into names and values and refuses its own options here.
  private fun recipeArguments(tokens: List<String>): Map<String, List<String>> {
    val arguments = linkedMapOf<String, MutableList<String>>()
    var index = 0
    while (index < tokens.size) {
      val token = tokens[index++]
      val name = token.substringBefore('=')
      if (name in BEFORE_RECIPE) throw UsageError("$name goes before the recipe")
      // --repo, --script, --plan and --set are refused rather than passed on, so a Cook option typed after the recipe
      // never becomes a recipe input.
      if (!name.matches(Regex("--[a-z][a-z0-9-]*")) || name in RESERVED) throw UsageError("Unknown option for the recipe: $token")
      val inline = if ('=' in token) token.substringAfter('=') else null
      val value = inline ?: tokens.getOrNull(index)?.also { index++ }
      if (value.isNullOrEmpty() || (inline == null && value.startsWith("--"))) throw UsageError("Missing value for $name")
      arguments.getOrPut(name.removePrefix("--")) { mutableListOf() }.add(value)
    }
    return arguments
  }

  private fun sets(values: List<String>): Map<String, List<String>> {
    val preferences = linkedMapOf<String, MutableList<String>>()
    values.forEach { value ->
      val separator = value.indexOf('=')
      if (separator <= 0) throw UsageError("--set requires name=value")
      val name = value.substring(0, separator)
      if (preferences.containsKey(name)) throw UsageError("Argument may only be supplied once: $name")
      preferences[name] = mutableListOf(value.substring(separator + 1))
    }
    return preferences
  }

  private fun single(preferences: Map<String, List<String>>): Map<String, Any> = preferences.mapValues { it.value.single() }

  private fun libraries(
    kitchen: Path,
    values: List<String>,
  ): List<Path> = values.map { path(kitchen, it, "--library") }

  private fun kitchenOf(command: Any): String? =
    when (command) {
      is InitArgs -> command.kitchen.kitchen
      is CookArgs -> command.kitchen.kitchen
      is PlanArgs -> command.kitchen.kitchen
      is ApplyArgs -> command.kitchen.kitchen
      is HelpArgs -> command.kitchen.kitchen
      else -> null
    }

  private fun findRecipe(
    repository: Path,
    name: String,
  ): Path {
    val directory = repository.resolve("cookbook/recipes/$name/recipe.groovy")
    val flat = repository.resolve("cookbook/recipes/$name.groovy")
    if (Files.isRegularFile(directory) && Files.isRegularFile(flat)) {
      throw IllegalArgumentException("Ambiguous recipe $name: $directory and $flat")
    }
    return if (Files.isRegularFile(flat)) flat else directory
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
        } else if (operand.endsWith(".groovy") && !operand.contains("://")) {
          val script = path(kitchen, operand, "recipe")
          if (!Files.isRegularFile(script)) throw IllegalArgumentException("Recipe file not found: $operand")
          LocalRecipe(script, null)
        } else {
          if (!operand.matches(RECIPE_NAME)) throw UsageError("Invalid recipe name: $operand")
          LocalRecipe(findRecipe(kitchen.toAbsolutePath(), operand), operand)
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
    if (Files.isRegularFile(command.script)) {
      val name = command.script.fileName.toString().removeSuffix(".groovy")
      RecipeSource.texts(Files.readString(command.script), command.script.fileName.toString())?.let {
        return render(command.expectedName ?: name, it)
      }
    }
    val declaration = declaration(command.script)
    if (command.expectedName != null && declaration.name != command.expectedName) {
      throw IllegalArgumentException("Recipe identity does not match: ${command.expectedName}")
    }
    val arguments = declaration.arguments.joinToString("\n") { input ->
      when (input) {
        is RecipeInput.RequiredPath -> "  --${input.name} PATH  required, once"
        is RecipeInput.RequiredChoices ->
          "  --${input.name} CHOICE  required, repeatable; choices: ${input.choices.joinToString(", ")}"
        is RecipeInput.OptionalChoices ->
          "  --${input.name} CHOICE  optional, once; choices: ${input.choices.joinToString(", ")}"
      }
    }
    return "Usage: cook [-C DIR] [--dry-run] [--remove] ${declaration.name} [arguments]\n$arguments"
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

  // Each recipe's consequence the diff cannot show, then the diff itself: what one approval covers.
  private fun review(
    proposal: Proposal,
    paths: Collection<String>,
  ) {
    proposal.recipes.filter { it.confirm != null }.forEach {
      stdout.println("Confirm ${it.address}")
      stdout.println(indent(it.confirm))
      stdout.println()
    }
    paths.forEach { path ->
      val before = lines(proposal.before(path))
      val after = lines(proposal.after(path))
      val diff =
        UnifiedDiffUtils.generateUnifiedDiff(
          if (proposal.before(path) == null) "/dev/null" else "a/$path",
          if (proposal.after(path) == null) "/dev/null" else "b/$path",
          before,
          DiffUtils.diff(before, after),
          3,
        )
      // java-diff-utils numbers an empty side from 1; git and GNU diff write it as 0,0.
      diff.forEach { line ->
        stdout.println(if (line.startsWith("@@")) line.replace(" -1,0 ", " -0,0 ").replace(" +1,0 ", " +0,0 ") else line)
      }
    }
    stdout.println()
    stdout.flush()
  }

  private fun lines(text: String?): List<String> = text?.removeSuffix("\n")?.split("\n") ?: emptyList()

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

  private fun declaration(script: Path): RecipeDeclaration {
    val source = script.toAbsolutePath()
    val parts = source.iterator().asSequence().toList()
    val recipesIndex = parts.indexOfLast { it.toString() == "recipes" }
    val cookbook = if (recipesIndex < 0) source.parent else
      source.root.resolve(parts.take(recipesIndex).fold(Path.of("")) { path, part -> path.resolve(part) })
    val relative = if (recipesIndex < 0) source.fileName.toString() else parts.drop(recipesIndex).joinToString("/")
    return Cookbook(cookbook.toFile()).declarationFile(relative, null)
  }

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

  private data class LocalRecipe(val script: Path, val expectedName: String?) : HelpTarget

  private data class Address(val coordinates: Coordinates) : HelpTarget

  private data class Init(
    val repository: Path,
  ) : Command

  private data class Cook(
    val repository: Path,
    val script: Path,
    val libraries: List<Path>,
    val preferences: Map<String, Any>,
    val dryRun: Boolean,
    val remove: Boolean,
    val removalApplications: Set<String>,
  ) : Command

  private data class CookCoordinates(
    val repository: Path,
    val requests: List<RecipeRequest>,
    val dryRun: Boolean,
    val batchApprove: Boolean,
    val existing: ExistingSeed,
  ) : Command

  private data class Plan(
    val repository: Path,
    val script: Path,
    val savedPlan: Path,
    val libraries: List<Path>,
    val preferences: Map<String, Any>,
  ) : Command

  private data class Apply(
    val repository: Path,
    val savedPlan: Path,
  ) : Command

  private class UsageError(
    message: String,
  ) : IllegalArgumentException(message)

  private companion object {
    val RECIPE_NAME = Regex("[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)*")
    val BEFORE_RECIPE = setOf("-C", "--kitchen", "--dry-run", "--batch-approve", "--force", "--skip", "--remove", "--library")
    val RESERVED = setOf("--repo", "--script", "--plan", "--set")
    val STATUS =
      mapOf("create" to "Created", "update" to "Updated", "delete" to "Deleted", "replace" to "Replaced", "keep" to "Kept")
  }
}
