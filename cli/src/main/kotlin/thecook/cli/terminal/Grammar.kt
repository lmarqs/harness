package thecook.cli.terminal

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Cook's own grammar: its commands and the options that precede a recipe. Parsing stops at the first operand, so
 * everything after a recipe is that recipe's arguments and is never read as a Cook option.
 */
internal fun grammar(): CommandLine =
  CommandLine(RootArgs())
    .setStopAtPositional(true)
    .setOverwrittenOptionsAllowed(false)

/** The directory Cook cooks in, accepted by every command. */
internal class KitchenOption {
  @Option(
    names = ["-C", "--kitchen"],
    paramLabel = "DIR",
    description = ["Cook in DIR, as if started there; relative paths resolve against it."],
  )
  var kitchen: String? = null
}

@Command(
  name = "cook",
  subcommands = [InitArgs::class, CookArgs::class, PlanArgs::class, ApplyArgs::class, HelpArgs::class],
  description = ["Applies a recipe: Cook's options come first, then the recipe, then the recipe's own arguments."],
  footer = [
    "",
    "A recipe is a .groovy path or a name resolved to cookbook/recipes/<name>.groovy or " +
      "cookbook/recipes/<name>/recipe.groovy, then packaged cookbook resources. Recipe arguments are " +
      "--name=value or --name value, checked against the recipe's declaration.",
    "A recipe can also be coordinates, group:artifact:version[:recipe], resolved from the repositories listed in " +
      "the kitchen's cook.toml. After coordinates, arguments are written --name=value, or --name for a yes-or-no " +
      "argument, and each word without -- starts the next recipe: ./cook a:b:1.0.0:x --name=value a:b:1.0.0:y",
    "--help takes no other arguments; read a recipe with help <recipe>.",
  ],
)
internal class RootArgs {
  @Mixin var kitchen = KitchenOption()

  @Option(names = ["--dry-run"], description = ["Report the proposed changes without writing."])
  var dryRun = false

  @Option(
    names = ["--batch-approve"],
    description = ["Show the recipes' confirm texts and the diff, then write without asking."],
  )
  var batchApprove = false

  @Option(
    names = ["--force"],
    description = ["Replace a file that a recipe's first apply writes from a template, instead of refusing."],
  )
  var force = false

  @Option(
    names = ["--skip"],
    description = ["Keep a file that a recipe's first apply writes from a template, and apply the rest."],
  )
  var skip = false

  @Option(names = ["--remove"], description = ["Remove the applications the recipe arguments select."])
  var remove = false

  @Option(names = ["--library"], paramLabel = "PATH", description = ["Add a library to the recipe's classpath."])
  var libraries = mutableListOf<String>()

  @Parameters(paramLabel = "RECIPE [ARGUMENT]", description = ["The recipe, then its arguments."])
  var operands = mutableListOf<String>()
}

@Command(name = "init", description = ["Create cook.groovy, keeping an existing one."])
internal class InitArgs {
  @Mixin var kitchen = KitchenOption()

  @Option(names = ["--remove"], description = ["Remove what the init recipe applied."])
  var remove = false
}

@Command(name = "cook", description = ["Cook the batch that a script returns, directly."])
internal class CookArgs {
  @Mixin var kitchen = KitchenOption()

  @Option(names = ["--dry-run"], description = ["Report the proposed changes without writing."])
  var dryRun = false

  @Option(names = ["--remove"], description = ["Remove the applications the arguments select."])
  var remove = false

  @Option(names = ["--script"], paramLabel = "PATH", description = ["The script; cook.groovy by default."])
  var script: String? = null

  @Option(names = ["--set"], paramLabel = "NAME=VALUE", description = ["A value the script reads."])
  var sets = mutableListOf<String>()

  @Option(names = ["--library"], paramLabel = "PATH", description = ["Add a library to the script's classpath."])
  var libraries = mutableListOf<String>()
}

@Command(name = "plan", description = ["Save the proposed changes as a plan."])
internal class PlanArgs {
  @Mixin var kitchen = KitchenOption()

  @Option(names = ["--script"], paramLabel = "PATH", description = ["The script; cook.groovy by default."])
  var script: String? = null

  @Option(
    names = ["--plan"],
    paramLabel = "PATH",
    description = ["Where to save the plan; .cook/plan.json by default."],
  )
  var plan: String? = null

  @Option(names = ["--set"], paramLabel = "NAME=VALUE", description = ["A value the script reads."])
  var sets = mutableListOf<String>()

  @Option(names = ["--library"], paramLabel = "PATH", description = ["Add a library to the script's classpath."])
  var libraries = mutableListOf<String>()
}

@Command(name = "apply", description = ["Apply a saved plan without evaluating the script again."])
internal class ApplyArgs {
  @Mixin var kitchen = KitchenOption()

  @Option(names = ["--plan"], paramLabel = "PATH", description = ["The saved plan; .cook/plan.json by default."])
  var plan: String? = null
}

@Command(name = "help", description = ["Print each recipe's help in order, without running it; alone, this usage."])
internal class HelpArgs {
  @Mixin var kitchen = KitchenOption()

  @Parameters(paramLabel = "RECIPE", description = ["A recipe path, name or coordinates."])
  var recipes = mutableListOf<String>()
}
