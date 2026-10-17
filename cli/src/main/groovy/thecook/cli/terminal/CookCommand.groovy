package thecook.cli.terminal

import picocli.CommandLine
import thecook.cookbook.Argument
import thecook.cookbook.CookbookApply
import thecook.cookbook.CookbookArtifact
import thecook.cookbook.CookbookDiscard
import thecook.cookbook.CookbookRepositories
import thecook.cookbook.Coordinates
import thecook.cookbook.ExistingSeed
import thecook.cookbook.Proposal
import thecook.cookbook.RecipeLoader
import thecook.cookbook.RecipeRequest
import thecook.cookbook.RecipeSource
import thecook.model.RecipeTexts

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Function

/**
 * Cook's command line. `stdin` answers the approval question; without it, writing needs `--batch-approve`. Coordinates
 * resolve from the repositories that `repositories` reads for a kitchen.
 */
final class CookCommand {
  private static final Set<String> BEFORE_RECIPE =
  ['-C', '--kitchen', '--dry-run', '--batch-approve', '--force', '--skip'] as Set
  private static final Map<String, String> STATUS =
  [create: 'Created', update: 'Updated', delete: 'Deleted', replace: 'Replaced', keep: 'Kept']

  private final PrintWriter stdout
  private final PrintWriter stderr
  private final Function<File, CookbookRepositories> repositories
  private final BufferedReader stdin

  CookCommand(PrintWriter stdout, PrintWriter stderr, Function<File, CookbookRepositories> repositories,
  BufferedReader stdin = null) {
    this.stdout = stdout
    this.stderr = stderr
    this.repositories = repositories
    this.stdin = stdin
  }

  int execute(String[] args) {
    Closure<Boolean> command

    try {
      command = parse(args)
    } catch (UsageError error) {
      stderr.println(error.message)
      stderr.println('Run ./cook help for usage.')
      stderr.flush()
      return 2
    } catch (IllegalArgumentException error) {
      stderr.println(error.message)
      stderr.flush()
      return 1
    }

    try {
      if (!command()) {
        return 1
      }

      stdout.flush()
      return 0
    } catch (Exception error) {
      stderr.println(error.message ?: error.class.simpleName)
      stderr.flush()
      return 1
    }
  }

  // The command the invocation names, as an action that returns false when it declines to write.
  private Closure<Boolean> parse(String[] invocation) {
    if (invocation.toList() == ['--help']) {
      return usage()
    }

    if (invocation.any { it.split('=', 2)[0] == '--help' }) {
      throw new UsageError('--help takes no other arguments; use help <recipe> to read a recipe')
    }

    def line = Grammar.commandLine()
    CommandLine.ParseResult parsed

    try {
      parsed = line.parseArgs(invocation)
    } catch (CommandLine.ParameterException error) {
      throw new UsageError(error.message ?: 'Invalid arguments')
    }

    RootArgs root = line.command
    def chosen = parsed.subcommand()
    def command = chosen?.commandSpec()?.userObject()

    if (command != null && (root.dryRun || root.batchApprove || root.force || root.skip)) {
      throw new UsageError('--dry-run, --batch-approve, --force and --skip go before a recipe, not before a command')
    }

    if (root.force && root.skip) {
      throw new UsageError('--force and --skip exclude each other')
    }

    def commandKitchen = command instanceof KitchenScoped ? command.kitchen.kitchen : null
    def kitchens = [root.kitchen.kitchen, commandKitchen].findAll { it != null }

    if (kitchens.size() > 1) {
      throw new UsageError('Option may only be supplied once: --kitchen')
    }

    def kitchen = kitchen(kitchens ? kitchens.first() : null).toAbsolutePath().toFile()

    switch (command) {
      case null:
        return recipes(kitchen, root)
      case HelpArgs:
        return help(kitchen, ((HelpArgs) command).recipes)
      case DiscardArgs:
        return discard(kitchen, (DiscardArgs) command)
      default:
        throw new IllegalStateException("Unhandled command: ${command}")
    }
  }

  private Closure<Boolean> usage() {
    return { -> stdout.print(Grammar.commandLine().usageMessage); true }
  }

  private Closure<Boolean> recipes(File kitchen, RootArgs root) {
    def first = root.operands ? root.operands.first() : null

    if (first == null) {
      throw new UsageError('A recipe is required')
    }

    if (!isOperand(first)) {
      throw new UsageError("Not a recipe: ${first}. A recipe is coordinates or a .groovy path")
    }

    List<String> operands = []
    List<List<Argument>> arguments = []
    String bare = null
    root.operands.each { token ->
      if (token.startsWith('-')) {
        def argument = recipeArgument(token)
        arguments.last().add(argument)
        bare = argument.form == Argument.Form.BARE ? argument.name : null
      } else if (isOperand(token)) {
        operands.add(recipeOperand(token, bare))
        arguments.add([])
        bare = null
      } else {
        if (bare == null) {
          throw new UsageError("Not a recipe: ${token}. Recipe arguments start with --")
        }

        arguments.last()[-1] = Argument.separate(bare, token)
        bare = null
      }
    }
    def requests = [operands, arguments].transpose()
    .collect { new RecipeRequest((String) it[0], (List<Argument>) it[1]) }
    def existing = root.force ? ExistingSeed.REPLACE : root.skip ? ExistingSeed.KEEP : ExistingSeed.REFUSE
    return {
      ->
      def proposal = new CookbookApply(kitchen, repositories.apply(kitchen), existing).propose(requests)
      write(proposal, root.dryRun, root.batchApprove)
    }
  }

  private Closure<Boolean> discard(File kitchen, DiscardArgs args) {
    if (!args.recipes) {
      throw new UsageError('A recipe is required')
    }

    args.recipes.each { operand ->
      if (operand.startsWith('-')) {
        def name = operand.split('=', 2)[0]
        throw new UsageError(name in BEFORE_RECIPE ?
        "${name} goes before the first recipe" :
        "discard takes no recipe arguments: ${operand}")
      }

      if (!isOperand(operand)) {
        throw new UsageError("Not a recipe: ${operand}. A recipe is coordinates or a .groovy path")
      }
    }
    return { -> write(new CookbookDiscard(kitchen).propose(args.recipes), args.dryRun, args.batchApprove) }
  }

  private static boolean isOperand(String token) {
    token.contains('/') || token.contains(':') || token.endsWith('.groovy')
  }

  private static String recipeOperand(String token, String bare) {
    if (!Coordinates.written(token)) {
      return token
    }

    try {
      Coordinates.parse(token)
    } catch (IllegalArgumentException error) {
      if (bare != null) {
        throw new UsageError("Not a recipe: ${token}. To give it to --${bare}, write --${bare}=${token}")
      }

      throw error
    }

    token
  }

  private static Argument recipeArgument(String token) {
    def name = token.split('=', 2)[0]

    if (name in BEFORE_RECIPE) {
      throw new UsageError("${name} goes before the first recipe")
    }

    if (!(name ==~ /--[a-z][a-z0-9-]*/)) {
      throw new UsageError("Unknown option for the recipe: ${token}")
    }

    if (!token.contains('=')) {
      return Argument.bare(name.substring(2))
    }

    def value = token.substring(token.indexOf('=') + 1)

    if (value.isEmpty()) {
      throw new UsageError("Missing value for ${name}")
    }

    Argument.inline(name.substring(2), value)
  }

  private Closure<Boolean> help(File kitchen, List<String> operands) {
    if (operands.isEmpty()) {
      return usage()
    }

    // Each page takes the kitchen's repositories on demand, so help on paths alone never reads them.
    List<Closure<String>> pages = operands.collect { String operand ->
      if (operand.startsWith('-')) {
        throw new UsageError("help takes recipes only: ${operand}")
      }

      if (Coordinates.written(operand)) {
        def coordinates = Coordinates.parse(operand)
        return { Closure<CookbookRepositories> repositories -> addressHelp(coordinates, repositories()) }
      }

      if (!isOperand(operand)) {
        throw new UsageError("Not a recipe: ${operand}. A recipe is coordinates or a .groovy path")
      }

      RecipeLoader.name(kitchen.toPath().toAbsolutePath().normalize(), operand)
      def script = path(kitchen.toPath(), operand, 'recipe')

      if (!Files.isRegularFile(script)) {
        throw new IllegalArgumentException("Recipe file not found: ${operand}")
      }

      return { Closure<CookbookRepositories> repositories -> recipeHelp(script, operand) }
    }
    return {
      ->
      CookbookRepositories resolved = null
      def resolve = { -> resolved = resolved ?: repositories.apply(kitchen) }
      stdout.println(pages.collect { it(resolve) }.join('\n\n'))
      true
    }
  }

  private static String addressHelp(Coordinates coordinates, CookbookRepositories repositories) {
    def artifact = repositories.artifact(coordinates)
    def recipe = coordinates.recipe

    if (recipe == null) {
      return frontPage(artifact)
    }

    def texts = RecipeSource.texts(artifact.recipeSource(recipe), "${recipe}.groovy")

    if (texts == null) {
      throw new IllegalArgumentException("${coordinates} has no help: its first import carries no @help")
    }

    render(coordinates.toString(), texts)
  }

  private static String frontPage(CookbookArtifact artifact) {
    def coordinates = artifact.coordinates
    [
      coordinates.artifactAddress,
      artifact.readme?.trim(),
      section('Recipes', artifact.recipes.collect { coordinates.recipeAddress(it) }),
      section('Models', artifact.models.collect { coordinates.group + '.' + coordinates.artifact + '.' + it }),
    ].findAll { it != null }.join('\n\n')
  }

  // A titled list of indented lines, or nothing when there are none.
  private static String section(String title, List<String> lines) {
    lines ? ([title] + lines.collect { '  ' + it }).join('\n') : null
  }

  private static String recipeHelp(Path script, String operand) {
    def texts = RecipeSource.texts(Files.readString(script), script.fileName.toString())

    if (texts == null) {
      throw new IllegalArgumentException("${operand} has no help: its first import carries no @help")
    }

    render(operand, texts)
  }

  private static String render(String name, RecipeTexts texts) {
    def params = texts.params.collect { param ->
      def facts = [
        param.defaultValue != null ? "default: ${param.defaultValue}" : 'required',
        param.choices ? "choices: ${param.choices.join(', ')}" : null,
        param.many ? 'repeatable' : null,
      ].findAll { it != null }
      '  ' + param.names.collect { '--' + it + '=<' + param.type + '>' }.join(', ') +
      '\n      ' + (param.doc ?: '') + '; ' + facts.join('; ')
    }.join('\n')
    [
      name,
      texts.help,
      params ? "Parameters\n${params}" : null,
      section('Ingredients', texts.ingredients),
      texts.confirm != null ? "Confirm\n${indent(texts.confirm)}" : null,
      texts.yieldText != null ? "After cooking\n${indent(texts.yieldText)}" : null,
    ].findAll { it != null }.join('\n\n')
  }

  // Reviews the proposal, asks unless approved in advance, writes it and reports each path; false when declined.
  private boolean write(Proposal proposal, boolean dryRun, boolean batchApprove) {
    def actions = proposal.actions

    if (actions.isEmpty()) {
      stdout.println('Already cooked.')
      return true
    }

    review(proposal, proposal.changes)

    if (!dryRun && !batchApprove && !approved()) {
      stderr.println('Nothing written.')
      stderr.flush()
      return false
    }

    if (!dryRun) {
      proposal.commit()
    }

    actions.each { path, action -> stdout.println((dryRun ? 'Would ' + action : STATUS[action]) + ' ' + path) }

    if (!dryRun) {
      proposal.recipes.findAll { it.yieldText != null }.each {
        stdout.println()
        stdout.println("After cooking ${it.address}")
        stdout.println(indent(it.yieldText))
      }
    }

    true
  }

  // Each release whose sources changed under a recorded version, each recipe's consequence the diff cannot show, then
  // the diff itself: what one approval covers.
  private void review(Proposal proposal, Collection<String> paths) {
    proposal.changedSources.each {
      stdout.println("Changed source ${it.address}")
      stdout.println("  cook.lock recorded sha256 ${it.recorded}; the release now has sha256 ${it.current}")
      stdout.println('  its sources changed under the same version; review the diff before approving')
      stdout.println()
    }
    proposal.recipes.findAll { it.confirm != null }.each {
      stdout.println("Confirm ${it.address}")
      stdout.println(indent(it.confirm))
      stdout.println()
    }
    paths.each { path ->
      proposal.patch(path).each {
        stdout.println(it)
      }
    }
    stdout.println()
    stdout.flush()
  }

  private boolean approved() {
    if (stdin == null) {
      throw new IllegalArgumentException(
      'Cook asks before writing, and there is no terminal to ask in; run with --batch-approve to write without asking')
    }

    stdout.print('Write these changes? [y/N] ')
    stdout.flush()
    stdin.readLine()?.trim()?.toLowerCase() in ['y', 'yes']
  }

  private static String indent(String text) {
    text.split(/\r\n|\r|\n/, -1).collect { it.isEmpty() ? it : "  ${it}" }.join('\n')
  }

  private static Path path(Path kitchen, String value, String option) {
    try {
      return kitchen.resolve(value)
    } catch (InvalidPathException error) {
      throw new UsageError("Invalid path for ${option}: ${error.reason}")
    }
  }

  private static Path kitchen(String value) {
    if (value == null) {
      return Path.of('')
    }

    if (value.isEmpty()) {
      throw new UsageError('Missing value for --kitchen')
    }

    def directory = path(Path.of(''), value, '--kitchen')

    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("Kitchen directory not found: ${value}")
    }

    directory
  }

  private static final class UsageError extends IllegalArgumentException {
    UsageError(String message) {
      super(message)
    }
  }
}
