package thecook.cli.terminal

import thecook.application.CookService
import java.io.PrintWriter
import java.nio.file.InvalidPathException
import java.nio.file.Path

class CookCommand(
    private val service: CookService,
    private val stdout: PrintWriter,
    private val stderr: PrintWriter,
) {
    fun execute(args: Array<String>): Int {
        val command = try {
            parse(args)
        } catch (error: UsageError) {
            stderr.println(error.message)
            stderr.println(USAGE)
            stderr.flush()
            return 2
        }

        return try {
            when (command) {
                Help -> stdout.println(USAGE)
                is Init -> stdout.println("Ready: ${service.initialize(command.repository)}")
                is Cook -> {
                    val changes = service.cook(
                        command.repository, command.script, command.libraries,
                        command.preferences, command.dryRun,
                    )
                    if (changes.isEmpty()) stdout.println("Already cooked.")
                    val verb = if (command.dryRun) "Would cook" else "Cooked"
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
        val action = if (localScript) "cook" else entrypoint
        if (action !in setOf("init", "cook", "plan", "apply")) throw UsageError("Unknown command: $action")
        if (args.size == 2 && args[1] == "--help") return Help

        var repository = Path.of("").toAbsolutePath()
        var script: Path? = if (localScript) path(entrypoint, "recipe") else null
        var savedPlan: Path? = null
        var dryRun = false
        val libraries = mutableListOf<Path>()
        val preferences = linkedMapOf<String, String>()
        val seen = mutableSetOf<String>()
        if (localScript) seen.add("--script")
        var index = 1
        while (index < args.size) {
            val option = args[index++]
            if (action == "cook" && option == "--dry-run") {
                if (!seen.add(option)) throw UsageError("Option may only be supplied once: $option")
                dryRun = true
                continue
            }
            val allowed = option == "--repo" ||
                (action in setOf("plan", "apply") && option == "--plan") ||
                (action in setOf("cook", "plan") && option in setOf("--script", "--library", "--set"))
            if (!allowed) throw UsageError("Unknown option for $action: $option")
            if (option != "--set" && option != "--library" && !seen.add(option)) {
                throw UsageError("Option may only be supplied once: $option")
            }
            val value = args.getOrNull(index)
            if (value.isNullOrEmpty() || value.startsWith("--")) throw UsageError("Missing value for $option")
            index++
            when (option) {
                "--repo" -> repository = path(value, option)
                "--script" -> script = path(value, option)
                "--plan" -> savedPlan = path(value, option)
                "--library" -> libraries.add(path(value, option))
                "--set" -> {
                    val separator = value.indexOf('=')
                    if (separator <= 0) throw UsageError("--set requires name=value")
                    preferences[value.substring(0, separator)] = value.substring(separator + 1)
                }
            }
        }
        if (action == "init") return Init(repository)
        if (action == "cook") {
            return Cook(repository, script ?: repository.resolve("cook.groovy"), libraries, preferences, dryRun)
        }
        val file = savedPlan ?: repository.resolve(".cook/plan.json")
        return if (action == "plan") {
            Plan(repository, script ?: repository.resolve("cook.groovy"), file, libraries, preferences)
        } else {
            Apply(repository, file)
        }
    }

    private fun path(value: String, option: String): Path = try {
        Path.of(value)
    } catch (error: InvalidPathException) {
        throw UsageError("Invalid path for $option: ${error.reason}")
    }

    private sealed interface Command
    private data object Help : Command
    private data class Init(val repository: Path) : Command
    private data class Cook(
        val repository: Path,
        val script: Path,
        val libraries: List<Path>,
        val preferences: Map<String, String>,
        val dryRun: Boolean,
    ) : Command
    private data class Plan(
        val repository: Path,
        val script: Path,
        val savedPlan: Path,
        val libraries: List<Path>,
        val preferences: Map<String, String>,
    ) : Command
    private data class Apply(val repository: Path, val savedPlan: Path) : Command
    private class UsageError(message: String) : IllegalArgumentException(message)

    private companion object {
        val USAGE = """
            Usage:
              the-cook init [--repo path]
              the-cook cook [--dry-run] [--repo path] [--script path] [--set name=value] [--library path]
              the-cook path/to/recipe.groovy [--dry-run] [--repo path] [--set name=value] [--library path]
              the-cook plan [--repo path] [--script path] [--plan path] [--set name=value] [--library path]
              the-cook apply [--repo path] [--plan path]
              the-cook --help

            --repo defaults to the current directory.
            --script defaults to cook.groovy in the repository.
            --plan defaults to .cook/plan.json in the repository.
            init preserves an existing cook.groovy.
            cook reconciles the batch directly; --dry-run previews without writing.
            --set and --library may be repeated with cook and plan.
            apply uses the saved plan without evaluating the cookbook again.
        """.trimIndent()
    }
}
