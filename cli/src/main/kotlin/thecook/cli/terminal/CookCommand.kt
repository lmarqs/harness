package thecook.cli.terminal

import thecook.application.CookService
import thecook.cookbook.Cookbook
import thecook.cookbook.RecipeSource
import thecook.model.RecipeInput
import thecook.model.RecipeArguments
import thecook.model.RecipeDeclaration
import thecook.model.RecipeTexts
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class CookCommand(
  private val service: CookService,
  private val stdout: PrintWriter,
  private val stderr: PrintWriter,
) {
  fun execute(args: Array<String>): Int {
    val command =
      try {
        parse(args)
      } catch (error: UsageError) {
        stderr.println(error.message)
        stderr.println(USAGE)
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
          stdout.println(USAGE)
        }

        is RecipeHelp -> {
          stdout.println(command.recipes.joinToString("\n\n") { recipeHelp(it) })
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
    val (kitchen, args) = kitchen(invocation)
    if (args.contentEquals(arrayOf("--help"))) return Help
    if (args.firstOrNull() == "help") return help(kitchen, args.drop(1))
    if (args.any { it.substringBefore('=') == "--help" }) {
      throw UsageError("--help takes no other arguments; use help <recipe> to read a recipe")
    }
    val entrypoint = args.firstOrNull() ?: throw UsageError("A command is required")
    val localScript = entrypoint.endsWith(".groovy") && !entrypoint.contains("://")
    if (localScript && !Files.isRegularFile(path(kitchen, entrypoint, "recipe"))) {
      throw IllegalArgumentException("Recipe file not found: $entrypoint")
    }
    val removingInit = entrypoint == "init" && args.drop(1).contains("--remove")
    val namedRecipe = !localScript && entrypoint !in BUILTINS
    if (namedRecipe && !entrypoint.matches(RECIPE_NAME)) {
      throw UsageError("Invalid recipe name: $entrypoint")
    }
    val action = if (localScript || namedRecipe || removingInit) "cook" else entrypoint
    val recipeInput = namedRecipe || localScript || removingInit

    val repository = kitchen.toAbsolutePath()
    var script: Path? = if (localScript) path(kitchen, entrypoint, "recipe") else null
    var savedPlan: Path? = null
    var dryRun = false
    var remove = false
    val libraries = mutableListOf<Path>()
    val preferences = linkedMapOf<String, MutableList<String>>()
    val seen = mutableSetOf<String>()
    if (localScript || namedRecipe) seen.add("--script")
    var index = 1
    while (index < args.size) {
      val argument = args[index++]
      val option = argument.substringBefore('=')
      val inlineValue = if ('=' in argument) argument.substringAfter('=') else null
      if (action == "cook" && option == "--dry-run") {
        if (inlineValue != null) throw UsageError("--dry-run does not take a value")
        if (!seen.add(option)) throw UsageError("Option may only be supplied once: $option")
        dryRun = true
        continue
      }
      if (action == "cook" && option == "--remove") {
        if (inlineValue != null) throw UsageError("--remove does not take a value")
        if (!seen.add(option)) throw UsageError("Option may only be supplied once: $option")
        remove = true
        continue
      }
      if (option == "-C" || option == "--kitchen") throw UsageError("$option goes before the recipe")
      // --repo is refused rather than passed on, so a destination typed after the recipe never becomes a recipe input.
      val recipeOption =
        recipeInput && option.matches(Regex("--[a-z][a-z0-9-]*")) &&
          option !in setOf("--repo", "--script", "--plan", "--library", "--set", "--remove")
      val allowed =
        recipeOption ||
          (action in setOf("plan", "apply") && option == "--plan") ||
          (action in setOf("cook", "plan") && option in setOf("--script", "--library", "--set"))
      if (!allowed) throw UsageError("Unknown option for $action: $option")
      if (option != "--set" && option != "--library" && !recipeOption && !seen.add(option)) {
        throw UsageError("Option may only be supplied once: $option")
      }
      val value = inlineValue ?: args.getOrNull(index)
      if (value.isNullOrEmpty() || value.startsWith("--")) throw UsageError("Missing value for $option")
      if (inlineValue == null) index++
      when (option) {
        "--script" -> {
          script = path(kitchen, value, option)
        }

        "--plan" -> {
          savedPlan = path(kitchen, value, option)
        }

        "--library" -> {
          libraries.add(path(kitchen, value, option))
        }

        "--set" -> {
          val separator = value.indexOf('=')
          if (separator <= 0) throw UsageError("--set requires name=value")
          addPreference(preferences, value.substring(0, separator), value.substring(separator + 1), recipeInput)
        }

        else -> {
          addPreference(preferences, option.removePrefix("--"), value, recipeInput)
        }
      }
    }
    val inputs: Map<String, Any> = preferences.mapValues { (_, values) -> if (recipeInput) values.toList() else values.single() }
    if (action == "init") return Init(repository)
    if (action == "cook") {
      val recipe =
        if (removingInit) {
          repository.resolve("cookbook/recipes/init/recipe.groovy")
        } else if (namedRecipe) {
          findRecipe(repository, entrypoint)
        } else {
          script
            ?: repository.resolve("cook.groovy")
        }
      if (namedRecipe) {
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
      val removalApplications = if (remove) RecipeArguments.removal(declaration(recipe), preferences) else emptySet()
      return Cook(repository, recipe, libraries, inputs, dryRun, remove, removalApplications)
    }
    val file = savedPlan ?: repository.resolve(".cook/plan.json")
    return if (action == "plan") {
      Plan(repository, script ?: repository.resolve("cook.groovy"), file, libraries, inputs)
    } else {
      Apply(repository, file)
    }
  }

  private fun addPreference(
    preferences: MutableMap<String, MutableList<String>>,
    name: String,
    value: String,
    repeatable: Boolean,
  ) {
    if (!repeatable && preferences.containsKey(name)) throw UsageError("Argument may only be supplied once: $name")
    preferences.getOrPut(name) { mutableListOf() }.add(value)
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
      operands.map { operand ->
        if (operand.startsWith("-")) throw UsageError("help takes recipes only: $operand")
        if (operand.endsWith(".groovy") && !operand.contains("://")) {
          val script = path(kitchen, operand, "recipe")
          if (!Files.isRegularFile(script)) throw IllegalArgumentException("Recipe file not found: $operand")
          HelpTarget(script, null)
        } else {
          if (!operand.matches(RECIPE_NAME)) throw UsageError("Invalid recipe name: $operand")
          HelpTarget(findRecipe(kitchen.toAbsolutePath(), operand), operand)
        }
      },
    )
  }

  private fun recipeHelp(command: HelpTarget): String {
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
    return "Usage: cook [-C DIR] ${declaration.name} [options]\n$arguments\n  --dry-run  preview without writing\n  --remove  remove selected applications\n  --set name=value  legacy argument form"
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

  // The kitchen is read before anything else because the recipe path and every relative path resolve against it.
  private fun kitchen(args: Array<String>): Pair<Path, Array<String>> {
    var kitchen: String? = null
    var entrypoint: String? = null
    val rest = mutableListOf<String>()
    var index = 0
    while (index < args.size) {
      val argument = args[index++]
      val option = argument.substringBefore('=')
      val beforeRecipe = entrypoint == null || entrypoint in BUILTINS
      if (beforeRecipe && (argument == "-C" || option == "--kitchen")) {
        if (kitchen != null) throw UsageError("Option may only be supplied once: $option")
        val value = if (argument.startsWith("--kitchen=")) argument.substringAfter('=') else args.getOrNull(index++)
        if (value.isNullOrEmpty() || value.startsWith("-")) throw UsageError("Missing value for $option")
        kitchen = value
        continue
      }
      if (entrypoint == null) entrypoint = argument
      rest.add(argument)
    }
    if (kitchen == null) return Path.of("") to rest.toTypedArray()
    val directory = path(Path.of(""), kitchen, "--kitchen")
    if (!Files.isDirectory(directory)) throw IllegalArgumentException("Kitchen directory not found: $kitchen")
    return directory to rest.toTypedArray()
  }

  private sealed interface Command

  private data object Help : Command

  private data class RecipeHelp(val recipes: List<HelpTarget>) : Command

  private data class HelpTarget(val script: Path, val expectedName: String?)

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
    val BUILTINS = setOf("init", "cook", "plan", "apply", "help")
    val RECIPE_NAME = Regex("[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)*")

    val USAGE =
      """
      Usage:
        cook [-C dir] <recipe> [--name value | --name=value] [--dry-run] [--remove]
        the-cook init [-C dir]
        the-cook cook [-C dir] [--dry-run] [--script path] [--set name=value] [--library path]
        the-cook [-C dir] path/to/recipe.groovy [--name value | --name=value] [--dry-run] [--set name=value] [--library path]
        the-cook plan [-C dir] [--script path] [--plan path] [--set name=value] [--library path]
        the-cook apply [-C dir] [--plan path]
        the-cook help [-C dir] <recipe> [<recipe>]...
        the-cook --help

      -C dir, --kitchen dir: cook in dir, as if started there; relative paths resolve against it.
      It goes before the first recipe. Without it, the current directory is the kitchen.
      Named recipes resolve to cookbook/recipes/<name>.groovy or cookbook/recipes/<name>/recipe.groovy, then packaged cookbook resources.
      Both local forms for one name are ambiguous and rejected.
      Nested names such as agentic/codex are supported.
      Declared recipes validate required paths and repeatable choices before rendering.
      help prints each recipe's help in order; @help texts are read without running the recipe.
      Undeclared factories continue to receive scalar string options.
      --script defaults to cook.groovy in the repository.
      --plan defaults to .cook/plan.json in the repository.
      init preserves an existing cook.groovy.
      cook reconciles the batch directly; --dry-run previews without writing.
      --set and --library may be repeated with cook and plan.
      apply uses the saved plan without evaluating the cookbook again.
      """.trimIndent()
  }
}
