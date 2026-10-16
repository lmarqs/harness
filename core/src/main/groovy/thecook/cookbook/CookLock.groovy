package thecook.cookbook

import groovy.json.JsonOutput
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * `cook.lock`: for each applied recipe, its short name, its source, the source's sha256 and the sections, keys, whole
 * files and links it owns. It is the record Cook reads to withdraw what a recipe no longer declares.
 */
final class CookLock {
  static final String PATH = 'cook.lock'
  private static final String HEADER = '# Written by the-cook; do not edit by hand.'
  // In the order the lock lists them, each read from its `<kind>s` group.
  private static final Map<String, Closure<Target>> KINDS = [
    section: { Map item -> new SectionTarget((String) item.file, (String) item.id) },
    key: { Map item -> new KeyTarget((String) item.file, ((List) item.path)*.toString()) },
    file: { String file -> new FileTarget(file) },
    link: { String file -> new LinkTarget(file) },
  ]

  private final Map<String, Entry> entries = new TreeMap<>()

  /** The lock in `text`, or an empty lock when `text` is `null`. */
  static CookLock parse(String text) {
    def lock = new CookLock()
    if (text == null) {
      return lock
    }
    Map data
    try {
      data = (Map) new Yaml(new SafeConstructor(new LoaderOptions())).load(text)
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("${PATH} is not valid YAML: ${error.message}", error)
    }
    if (data?.version != 1 || !(data.recipes instanceof List)) {
      throw new IllegalArgumentException("${PATH} must have version: 1 and a list of recipes")
    }
    data.recipes.each { Map recipe ->
      def owns = (Map) (recipe.owns ?: [:])
      def targets = KINDS.collectMany { kind, create -> (owns["${kind}s"] ?: []).collect(create) }
      lock.entries[recipe.name] = new Entry(recipe.name, recipe.source, recipe.sha256, targets)
    }
    lock
  }

  /** What the recipe `name` owned when it was last applied; empty when it never was. */
  List<Target> owned(String name) {
    entries[name]?.targets ?: []
  }

  /** The recipe that owns `target`, other than `except`, or `null`. */
  String ownerOf(Target target, String except) {
    entries.find { name, entry ->
      name != except && entry.targets.any {
        it.overlaps(target)
      }
    }?.key
  }

  /** Records `name` as applied from `source`, owning `targets`, and takes each target from the recipes that held it. */
  void record(String name, String source, String sha256, List<Target> targets) {
    entries.each { other, entry ->
      if (other != name) {
        entry.targets.removeAll { owned ->
          targets.any {
            it.overlaps(owned)
          }
        }
      }
    }
    entries[name] = new Entry(name, source, sha256, targets)
  }

  /** The lock's YAML, with every string quoted so no value is read as another type. */
  String render() {
    def lines = [HEADER, 'version: 1', entries ? 'recipes:' : 'recipes: []']
    entries.values().each { entry ->
      lines << "  - name: ${quote(entry.name)}"
      lines << "    source: ${quote(entry.source)}"
      lines << "    sha256: ${quote(entry.sha256)}"
      def groups = KINDS.keySet().collectEntries { kind -> [(kind): entry.targets.findAll { it.kind == kind }] }
      .findAll { kind, targets -> targets }
      lines << (groups ? '    owns:' : '    owns: {}')
      groups.each { kind, List<Target> targets ->
        lines << "      ${kind}s:"
        targets.sort { it.toString() }.each { lines.addAll(item(it.entry)) }
      }
    }
    lines.join('\n') + '\n'
  }

  private static String quote(String value) {
    JsonOutput.toJson(value)
  }

  /** A target's lines in its group: its fields, or its path for a whole file or link. */
  private static List<String> item(Object entry) {
    if (!(entry instanceof Map)) {
      return ["        - ${JsonOutput.toJson(entry)}".toString()]
    }
    ((Map) entry).collect { field, value -> "${field}: ${JsonOutput.toJson(value)}".toString() }
    .withIndex().collect { line, index -> (index ? '          ' : '        - ') + line }
  }

  private static final class Entry {
    final String name
    final String source
    final String sha256
    final List<Target> targets

    Entry(String name, String source, String sha256, List<Target> targets) {
      this.name = name
      this.source = source
      this.sha256 = sha256
      this.targets = new ArrayList<>(targets)
    }
  }
}
