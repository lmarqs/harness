package thecook.cli.terminal

import thecook.application.CookService
import thecook.cookbook.Cookbook
import thecook.model.RecipeInput
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
          stdout.println(recipeHelp(command))
        }

        is Init -> {
          stdout.println("Ready: ${service.initialize(command.repository)}")
        }

        is Cook -> {
          val changes =
            if (command.remove) {
              val harnesses = command.preferences["harness"]
              val selected = when (harnesses) {
                is List<*> -> harnesses.map { it.toString() }
                is String -> listOf(harnesses)
                else -> emptyList()
              }
              service.remove(command.repository, command.script, selected, command.dryRun)
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

  private fun parse(args: Array<String>): Command {
    if (args.contentEquals(arrayOf("--help"))) return Help
    val entrypoint = args.firstOrNull() ?: throw UsageError("A command is required")
    val localScript = entrypoint.endsWith(".groovy") && !entrypoint.contains("://")
    if (localScript && !Files.isRegularFile(path(entrypoint, "recipe"))) {
      throw IllegalArgumentException("Recipe file not found: $entrypoint")
    }
    val builtins = setOf("init", "cook", "plan", "apply")
    val removingInit = entrypoint == "init" && args.drop(1).contains("--remove")
    val namedRecipe = !localScript && entrypoint !in builtins
    if (namedRecipe && !entrypoint.matches(Regex("[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)*"))) {
      throw UsageError("Invalid recipe name: $entrypoint")
    }
    val action = if (localScript || namedRecipe || removingInit) "cook" else entrypoint
    val recipeInput = namedRecipe || localScript || removingInit
    val wantsHelp = args.drop(1).contains("--help")
    if (wantsHelp && !localScript && !namedRecipe) return Help

    var repository = Path.of("").toAbsolutePath()
    var script: Path? = if (localScript) path(entrypoint, "recipe") else null
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
      val recipeOption =
        recipeInput && option.matches(Regex("--[a-z][a-z0-9-]*")) &&
          option !in setOf("--repo", "--script", "--plan", "--library", "--set", "--help", "--remove")
      val allowed =
        recipeOption || option == "--repo" || option == "--help" ||
          (action in setOf("plan", "apply") && option == "--plan") ||
          (action in setOf("cook", "plan") && option in setOf("--script", "--library", "--set"))
      if (!allowed) throw UsageError("Unknown option for $action: $option")
      if (option == "--help") {
        if (inlineValue != null) throw UsageError("--help does not take a value")
        continue
      }
      if (option != "--set" && option != "--library" && !recipeOption && !seen.add(option)) {
        throw UsageError("Option may only be supplied once: $option")
      }
      val value = inlineValue ?: args.getOrNull(index)
      if (value.isNullOrEmpty() || value.startsWith("--")) throw UsageError("Missing value for $option")
      if (inlineValue == null) index++
      when (option) {
        "--repo" -> {
          repository = path(value, option)
        }

        "--script" -> {
          script = path(value, option)
        }

        "--plan" -> {
          savedPlan = path(value, option)
        }

        "--library" -> {
          libraries.add(path(value, option))
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
      if (wantsHelp) return if (recipeInput) RecipeHelp(recipe) else Help
      if (remove) {
        val supplied = inputs.keys - setOf("harness", "policy")
        if (supplied.isNotEmpty()) throw UsageError("Removal accepts only --harness: ${supplied.first()}")
        val selected = preferences["harness"].orEmpty()
        if (selected.any { it !in setOf("claude", "codex") }) throw UsageError("Unsupported harness for removal")
      }
      return Cook(repository, recipe, libraries, inputs, dryRun, remove)
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

  private fun recipeHelp(command: RecipeHelp): String {
    val source = command.script.toAbsolutePath()
    val parts = source.iterator().asSequence().toList()
    val recipesIndex = parts.indexOfLast { it.toString() == "recipes" }
    val cookbook = if (recipesIndex < 0) source.parent else
      source.root.resolve(parts.take(recipesIndex).fold(Path.of("")) { path, part -> path.resolve(part) })
    val relative = if (recipesIndex < 0) source.fileName.toString() else parts.drop(recipesIndex).joinToString("/")
    val name = if (recipesIndex < 0) null else
      relative.removePrefix("recipes/").removeSuffix("/recipe.groovy").removeSuffix(".groovy")
    val declaration = Cookbook(cookbook.toFile()).declarationFile(relative, name)
    val arguments = declaration.arguments.joinToString("\n") { input ->
      when (input) {
        is RecipeInput.RequiredPath -> "  --${input.name} PATH  required, once"
        is RecipeInput.RequiredChoices ->
          "  --${input.name} CHOICE  required, repeatable; choices: ${input.choices.joinToString(", ")}"
        is RecipeInput.OptionalChoices ->
          "  --${input.name} CHOICE  optional, once; choices: ${input.choices.joinToString(", ")}"
      }
    }
    return "Usage: cook ${declaration.name} [options]\n$arguments\n  --repo PATH  destination repository\n  --dry-run  preview without writing\n  --remove  remove selected applications\n  --set name=value  legacy argument form\n  --help  show this help"
  }

  private fun path(
    value: String,
    option: String,
  ): Path =
    try {
      Path.of(value)
    } catch (error: InvalidPathException) {
      throw UsageError("Invalid path for $option: ${error.reason}")
    }

  private sealed interface Command

  private data object Help : Command

  private data class RecipeHelp(val script: Path) : Command

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
    val USAGE =
      """
      Usage:
        cook <recipe> [--name value | --name=value] [--dry-run] [--remove] [--repo path]
        the-cook init [--repo path]
        the-cook cook [--dry-run] [--repo path] [--script path] [--set name=value] [--library path]
        the-cook path/to/recipe.groovy [--name value | --name=value] [--dry-run] [--repo path] [--set name=value] [--library path]
        the-cook plan [--repo path] [--script path] [--plan path] [--set name=value] [--library path]
        the-cook apply [--repo path] [--plan path]
        the-cook --help

      --repo defaults to the current directory.
      Named recipes resolve to cookbook/recipes/<name>.groovy or cookbook/recipes/<name>/recipe.groovy, then packaged cookbook resources.
      Both local forms for one name are ambiguous and rejected.
      Nested names such as agentic/codex are supported.
      Declared recipes validate required paths and repeatable choices before rendering.
      Use <recipe> --help to inspect required inputs and execution options.
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
