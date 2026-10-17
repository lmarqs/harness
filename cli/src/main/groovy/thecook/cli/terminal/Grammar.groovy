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
final class Grammar {
  private Grammar() {
  }

  static CommandLine commandLine() {
    new CommandLine(new RootArgs())
        .setStopAtPositional(true)
        .setOverwrittenOptionsAllowed(false)
  }
}

/** The directory Cook cooks in, accepted by every command. */
class KitchenOption {
  @Option(names = ['-C', '--kitchen'], paramLabel = 'DIR',
  description = ['Cook in DIR, as if started there; relative paths resolve against it.'])
  String kitchen
}

/** A command that accepts `-C`/`--kitchen`. */
abstract class KitchenScoped {
  @Mixin KitchenOption kitchen = new KitchenOption()
}

@Command(
name = 'cook',
subcommands = [HelpArgs, DiscardArgs, RevertArgs, BatchArgs],
description = ["Applies a recipe: Cook's options come first, then the recipe, then the recipe's own arguments."],
footer = [
  '',
  "A recipe is coordinates, group:artifact:version[:recipe], resolved from the repositories listed in the kitchen's " +
  'cook.toml, or a .groovy path inside the kitchen. Arguments follow their recipe as --name=value or --name value, ' +
  'and a yes-or-no argument as --name or --name=false. A word that contains / or : or ends with .groovy starts the ' +
  'next recipe: ./cook a:b:1.0.0:x --name value ./setup.groovy',
  '--help takes no other arguments; read a recipe with help <recipe>.',
]
)
class RootArgs extends KitchenScoped {
  @Option(names = ['--dry-run'], description = ['Report the proposed changes without writing.'])
  boolean dryRun

  @Option(names = ['--batch-approve'], description = ["Show the recipes' confirm texts and the diff, then write without asking."])
  boolean batchApprove

  @Option(names = ['--force'], description = ["Replace a file that a recipe's first apply writes from a template, instead of refusing."])
  boolean force

  @Option(names = ['--skip'], description = ["Keep a file that a recipe's first apply writes from a template, and apply the rest."])
  boolean skip

  @Parameters(paramLabel = 'RECIPE [ARGUMENT]', description = ['The recipe, then its arguments.'])
  List<String> operands = []
}

@Command(name = 'help', description = ["Print each recipe's help in order, without running it; alone, this usage."])
class HelpArgs extends KitchenScoped {
  @Parameters(paramLabel = 'RECIPE', description = ['A recipe: coordinates or a .groovy path.'])
  List<String> recipes = []
}

@Command(name = 'discard', description = ["Remove what cook.lock lists for each recipe, without running it."])
class DiscardArgs extends KitchenScoped {
  @Option(names = ['--dry-run'], description = ['Report the proposed changes without writing.'])
  boolean dryRun

  @Option(names = ['--batch-approve'], description = ['Show the diff, then write without asking.'])
  boolean batchApprove

  @Parameters(paramLabel = 'RECIPE', description = ['A recipe as apply takes it: coordinates or a .groovy path.'])
  List<String> recipes = []
}

@Command(name = 'revert', description = ['Reverse one recorded batch against the current content, without running its recipes.'])
class RevertArgs extends KitchenScoped {
  @Option(names = ['--batch-approve'], description = ['Show the diff, then write without asking.'])
  boolean batchApprove

  @Parameters(paramLabel = 'BATCH', description = ['The batch ID, as ./cook batch list shows it.'])
  String batch
}

@Command(name = 'batch', description = ['Read the recorded batches.'], subcommands = [BatchListArgs, BatchInspectArgs])
class BatchArgs {
}

@Command(name = 'list', description = ['Show the recorded batches, oldest first.'])
class BatchListArgs extends KitchenScoped {
}

@Command(name = 'inspect', description = ["Show one batch's recipes, arguments, files, patches and any failure."])
class BatchInspectArgs extends KitchenScoped {
  @Parameters(paramLabel = 'BATCH', description = ['The batch ID, as ./cook batch list shows it.'])
  String batch
}
