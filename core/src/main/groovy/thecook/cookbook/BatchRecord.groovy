package thecook.cookbook

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The record of one batch in the destination's `.cook/batches/<batchId>/`: `batch.yaml` with the recipes, their
 * arguments and order, the sources they consumed, each touched file's state before and after and the result;
 * `patches/<path>.patch` with each file's change; `sources/<n>-<name>` with each source; and `failure.log` when the
 * writes failed. The batch ID is the UTC time the batch started, with a
 * suffix when another batch started in the same second.
 */
final class BatchRecord {
  static final String DIRECTORY = '.cook/batches'
  private static final DateTimeFormatter ID = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC)

  final String id
  final File directory
  private Map batch

  private BatchRecord(String id, File directory) {
    this.id = id
    this.directory = directory
  }

  /** A new, empty record in `destination`, named by the time `started`. */
  static BatchRecord create(File destination, Instant started = Instant.now()) {
    def batches = new File(destination, DIRECTORY)
    batches.mkdirs()
    def base = ID.format(started)
    for (int suffix = 1; ; suffix++) {
      def id = suffix == 1 ? base : "${base}-${suffix}"
      def directory = new File(batches, id)
      // mkdir creates the directory only when it is absent, so two batches never share an ID.
      if (directory.mkdir()) {
        return new BatchRecord(id, directory)
      }
      if (!directory.isDirectory()) {
        throw new IllegalStateException("Cannot create the batch record ${directory}")
      }
    }
  }

  /**
   * Saves `batch` with the result `pending`, each file's patch and each source, keyed by its origin, before the batch
   * writes.
   */
  void save(Map batch, Map<String, List<String>> patches, Map<String, String> sources) {
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
    this.batch = [id: id] + batch + [sources: saved, result: 'pending']
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

  private void write() {
    def options = new DumperOptions()
    options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
    new File(directory, 'batch.yaml').setText(new Yaml(options).dump(batch), 'UTF-8')
  }
}
