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
  subcommands = [HelpArgs::class, RevertArgs::class],
  description = ["Applies a recipe: Cook's options come first, then the recipe, then the recipe's own arguments."],
  footer = [
    "",
    "A recipe is coordinates, group:artifact:version[:recipe], resolved from the repositories listed in the " +
      "kitchen's cook.toml, or a .groovy path inside the kitchen. Arguments follow their recipe as --name=value or " +
      "--name value, and a yes-or-no argument as --name or --name=false. A word that contains / or : or ends with " +
      ".groovy starts the next recipe: ./cook a:b:1.0.0:x --name value ./setup.groovy",
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

  @Parameters(paramLabel = "RECIPE [ARGUMENT]", description = ["The recipe, then its arguments."])
  var operands = mutableListOf<String>()
}

@Command(name = "help", description = ["Print each recipe's help in order, without running it; alone, this usage."])
internal class HelpArgs {
  @Mixin var kitchen = KitchenOption()

  @Parameters(paramLabel = "RECIPE", description = ["A recipe: coordinates or a .groovy path."])
  var recipes = mutableListOf<String>()
}

@Command(
  name = "revert",
  description = ["Reverse one recorded batch against the current content, without running its recipes."],
)
internal class RevertArgs {
  @Mixin var kitchen = KitchenOption()

  @Option(names = ["--batch-approve"], description = ["Show the diff, then write without asking."])
  var batchApprove = false

  @Parameters(paramLabel = "BATCH", description = ["The batch ID, as ./cook batch list shows it."])
  lateinit var batch: String
}
