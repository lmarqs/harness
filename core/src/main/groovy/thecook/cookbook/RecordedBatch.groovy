package thecook.cookbook

import org.yaml.snakeyaml.Yaml

/** A batch as its record in `.cook/batches/<batchId>/` keeps it. */
final class RecordedBatch {
  final String id
  /** `apply` or `revert`. */
  final String command
  /** `written`, `failed`, or `pending` when the writes never finished. */
  final String result
  /** The batch a revert reversed; `null` for an apply. */
  final String reverts
  final List<RecordedRecipe> recipes
  final List<RecordedFile> files
  private final File directory

  private RecordedBatch(File directory, Map batch) {
    this.directory = directory
    id = directory.name
    command = batch.command
    result = batch.result
    reverts = batch.reverts
    recipes = batch.recipes.collect { Map recipe ->
      new RecordedRecipe((String) recipe.address, new RecipeRequest((String) recipe.operand, recipe.arguments.collect { Map argument ->
        argument.form == 'BARE' ? Argument.bare((String) argument.name) : argument.form == 'INLINE' ?
            Argument.inline((String) argument.name, (String) argument.value) : Argument.separate((String) argument.name, (String) argument.value)
      }))
    } ?: []
    files = batch.files.collect { Map file -> new RecordedFile((String) file.path, (String) file.action, (Map) file.before, (Map) file.after) }
  }

  /** The batches recorded in `destination`, oldest first. */
  static List<RecordedBatch> list(File destination) {
    def batches = new File(destination, BatchRecord.DIRECTORY).listFiles()?.findAll { new File(it, 'batch.yaml').isFile() } ?: []
    // A suffix orders batches that started in the same second, so -10 follows -9.
    batches.sort { a, b -> a.name.replaceAll(/-(\d+)$/, '') <=> b.name.replaceAll(/-(\d+)$/, '') ?: suffix(a.name) <=> suffix(b.name) }
    .collect { load(it) }
  }

  /** The batch `id` recorded in `destination`; fails, pointing to `./cook batch list`, when none is. */
  static RecordedBatch read(File destination, String id) {
    def directory = new File(destination, "${BatchRecord.DIRECTORY}/${id}")
    if (!(id ==~ /[0-9A-Za-z][0-9A-Za-z-]*/) || !new File(directory, 'batch.yaml').isFile()) {
      throw new IllegalArgumentException("No batch ${id} is recorded in ${BatchRecord.DIRECTORY}; ./cook batch list shows the recorded batches")
    }
    load(directory)
  }

  /** The patch the batch recorded for `path`, line by line. */
  List<String> patch(String path) {
    FilePatch.lines(new File(directory, "patches/${path}.patch").getText('UTF-8'))
  }

  /** What the batch's failed writes left in `failure.log`; `null` when it has none. */
  String getFailure() {
    def log = new File(directory, 'failure.log')
    log.isFile() ? log.getText('UTF-8') : null
  }

  private static RecordedBatch load(File directory) {
    new RecordedBatch(directory, (Map) new Yaml().load(new File(directory, 'batch.yaml').getText('UTF-8')))
  }

  private static int suffix(String id) {
    def found = id =~ /Z-(\d+)$/
    found ? found[0][1] as int : 1
  }
}

/** A recipe a recorded batch applied: its address, and its operand and arguments as written. */
final class RecordedRecipe {
  final String address
  final RecipeRequest request

  RecordedRecipe(String address, RecipeRequest request) {
    this.address = address
    this.request = request
  }
}

/**
 * A file a recorded batch touched: its path, what happened to it, and its state before and after, as
 * `Draft.recordedBefore` describes it.
 */
final class RecordedFile {
  final String path
  final String action
  final Map before
  final Map after

  RecordedFile(String path, String action, Map before, Map after) {
    this.path = path
    this.action = action
    this.before = before
    this.after = after
  }
}
