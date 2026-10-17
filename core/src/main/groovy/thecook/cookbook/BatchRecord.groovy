package thecook.cookbook

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import thecook.filesystem.AtomicFiles

import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * The record of one batch in the destination's `.cook/batches/<batchId>/`: `batch.yaml` with the recipes, their
 * arguments and order, the sources they consumed, each touched file's state before and after and the result;
 * `patches/<path>.patch` with each file's change; `sources/<n>-<name>` with each source; and `failure.log` when the
 * writes failed. The batch ID is the UTC time the batch started, with a suffix when another batch started in the same
 * second.
 */
final class BatchRecord {
  static final String DIRECTORY = '.cook/batches'
  private static final DateTimeFormatter STARTED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC)
  private static final Pattern ID = ~/(\d{4}-\d\d-\d\dT\d\d-\d\d-\d\dZ)(?:-(\d+))?/

  final String id
  private final File directory
  private Map batch

  private BatchRecord(String id, File directory, Map batch) {
    this.id = id
    this.directory = directory
    this.batch = batch
  }

  /** A new, empty record in `destination`, named by the time `started`. */
  static BatchRecord create(File destination, Instant started = Instant.now()) {
    def batches = new File(destination, DIRECTORY)
    // A link here would put the record outside the kitchen, like a declared write through a link.
    for (def part = batches; part != destination; part = part.parentFile) {
      if (Files.isSymbolicLink(part.toPath())) {
        throw new IllegalStateException("${destination.toPath().relativize(part.toPath())} is a symbolic link; " +
        'batch records are kept only inside the kitchen')
      }
    }
    batches.mkdirs()
    def base = STARTED.format(started)
    for (int suffix = 1; ; suffix++) {
      def id = suffix == 1 ? base : "${base}-${suffix}".toString()
      def directory = new File(batches, id)
      // mkdir creates the directory only when it is absent, so two batches never share an ID.
      if (directory.mkdir()) {
        return new BatchRecord(id, directory, null)
      }
      if (!directory.isDirectory()) {
        throw new IllegalStateException("Cannot create the batch record ${directory}")
      }
    }
  }

  /** The batches recorded in `destination`, oldest first. */
  static List<BatchRecord> list(File destination) {
    def ids = new File(destination, DIRECTORY).list()?.findAll { recorded(destination, it) } ?: []
    ids.sort { order(it) }.collect { load(destination, it) }
  }

  /** The batch `id` recorded in `destination`; fails, pointing to `./cook batch list`, when none is. */
  static BatchRecord read(File destination, String id) {
    if (!recorded(destination, id)) {
      throw new IllegalArgumentException("No batch ${id} is recorded in ${DIRECTORY}; ./cook batch list shows the recorded batches")
    }
    load(destination, id)
  }

  /**
   * Saves the batch before it writes, with the result `pending`: its `command`, `apply` or `revert`; the batch a
   * revert `reverts`; its `recipes` in order; each file it touches with that file's patch; and each source it
   * consumed, by its origin.
   */
  void save(String command, String reverts, List<RecordedRecipe> recipes, List<RecordedFile> files,
      Map<String, List<String>> patches, Map<String, String> sources) {
    patches.each { path, lines ->
      def patch = new File(directory, "patches/${path}.patch")
      patch.parentFile.mkdirs()
      patch.setText(lines.collect { "${it}\n" }.join(), 'UTF-8')
    }
    List<Map> saved = []
    sources.eachWithIndex { origin, text, index ->
      def file = "sources/${index + 1}-${origin.tokenize('/:').last()}".toString()
      def snapshot = new File(directory, file)
      snapshot.parentFile.mkdirs()
      snapshot.setText(text, 'UTF-8')
      saved.add([origin: origin, file: file])
    }
    batch = [id: id, command: command] + (reverts ? [reverts: reverts] : [:]) + [
      recipes: recipes.collect { recipe ->
        [operand: recipe.request.operand, address: recipe.address, sha256: recipe.sha256,
          arguments: recipe.request.arguments.collect { [name: it.name, value: it.value, form: it.form.name()] }]
      },
      files: files.collect { [path: it.path, action: it.action, before: it.before, after: it.after] },
      sources: saved,
      result: 'pending',
    ]
    write()
  }

  /** Records that the batch wrote its changes. */
  void written() {
    batch.result = 'written'
    write()
  }

  /** Records that the batch failed, and `failure` in `failure.log`. */
  void failed(Throwable failure) {
    def log = new StringWriter()
    failure.printStackTrace(new PrintWriter(log))
    new File(directory, 'failure.log').setText(log.toString(), 'UTF-8')
    batch.result = 'failed'
    write()
  }

  /** `apply` or `revert`. */
  String getCommand() {
    batch.command
  }

  /** `written`, `failed`, or `pending` when the writes never finished. */
  String getResult() {
    batch.result
  }

  /** The batch a revert reversed; `null` for an apply. */
  String getReverts() {
    batch.reverts
  }

  /** The recipes the batch applied, in order. */
  List<RecordedRecipe> getRecipes() {
    batch.recipes.collect { Map recipe ->
      def arguments = recipe.arguments.collect { Map argument ->
        Argument.of((String) argument.name, (String) argument.value, Argument.Form.valueOf((String) argument.form))
      }
      new RecordedRecipe(new RecipeRequest((String) recipe.operand, arguments), (String) recipe.address, (String) recipe.sha256)
    }
  }

  /** The files the batch touched, in path order. */
  List<RecordedFile> getFiles() {
    batch.files.collect { Map file -> new RecordedFile((String) file.path, (String) file.action, (Map) file.before, (Map) file.after) }
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

  private void write() {
    def options = new DumperOptions()
    options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    // Replaced whole, so a write cut short never leaves a record that cannot be read.
    new AtomicFiles().write(new File(directory, 'batch.yaml'), new Yaml(options).dump(batch), 'rw-r--r--')
  }

  // Only a name in the ID format is read, so an ID never reaches outside the batches' directory.
  private static boolean recorded(File destination, String id) {
    ID.matcher(id).matches() && new File(destination, "${DIRECTORY}/${id}/batch.yaml").isFile()
  }

  private static BatchRecord load(File destination, String id) {
    def directory = new File(destination, "${DIRECTORY}/${id}")
    new BatchRecord(id, directory, (Map) new Yaml().load(new File(directory, 'batch.yaml').getText('UTF-8')))
  }

  // The time the batch started, then its suffix, padded so -10 sorts after -9.
  private static String order(String id) {
    def matcher = ID.matcher(id)
    matcher.matches()
    "${matcher.group(1)}-${(matcher.group(2) ?: '1').padLeft(9, '0')}"
  }
}

/** A recipe a recorded batch applied: its operand and arguments as written, its address and its source's sha256. */
final class RecordedRecipe {
  final RecipeRequest request
  final String address
  final String sha256

  RecordedRecipe(RecipeRequest request, String address, String sha256) {
    this.request = request
    this.address = address
    this.sha256 = sha256
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

  /** Whether a file stood at the path before and after the batch, so that the batch changed only its lines or mode. */
  boolean isEdit() {
    before?.kind == 'file' && after?.kind == 'file'
  }
}
