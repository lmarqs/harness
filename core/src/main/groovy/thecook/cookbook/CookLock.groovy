package thecook.cookbook

import groovy.json.JsonOutput
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * `cook.lock`: for each applied recipe, its short name, its source, the source's sha256 and the sections, keys, whole
 * files and links it owns. The destination commits it, so reviewers read ownership changes in pull requests.
 */
final class CookLock {
  static final String PATH = 'cook.lock'
  private static final String HEADER = '# Written by the-cook. Review changes in pull requests; do not edit by hand.'
  private static final Map<String, String> GROUPS = [sections: 'section', keys: 'key', files: 'file', links: 'link']

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
      def targets = GROUPS.collectMany { group, kind ->
        (owns[group] ?: []).collect { item ->
          switch (kind) {
            case 'section': return new Target(kind, item.file, item.id)
            case 'key': return new Target(kind, item.file, item.path)
            default: return new Target(kind, item, null)
          }
        }
      }
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
      def groups = GROUPS.collectEntries { group, kind -> [(group): entry.targets.findAll { it.kind == kind }] }
      .findAll { group, targets -> targets }
      lines << (groups ? '    owns:' : '    owns: {}')
      groups.each { group, List<Target> targets ->
        lines << "      ${group}:"
        targets.sort { it.toString() }.each { target ->
          switch (target.kind) {
            case 'section':
              lines << "        - file: ${quote(target.file)}" << "          id: ${quote(target.address)}"
              break
            case 'key':
              lines << "        - file: ${quote(target.file)}" << "          path: ${JsonOutput.toJson(target.address)}"
              break
            default:
              lines << "        - ${quote(target.file)}"
          }
        }
      }
    }
    lines.join('\n') + '\n'
  }

  private static String quote(Object value) {
    JsonOutput.toJson(value?.toString())
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
