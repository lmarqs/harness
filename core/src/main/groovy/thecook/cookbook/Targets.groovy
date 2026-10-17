package thecook.cookbook

import thecook.formats.BlockDocument
import thecook.formats.Document
import thecook.formats.Documents
import thecook.reconciliation.Permissions
import thecook.rendering.BlockLocation
import thecook.rendering.KeyLocation

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * A part of a destination file that a recipe owns: it writes the part, withdraws it, and is what `cook.lock` records.
 * Two targets are the same when the lock records them the same.
 */
abstract class Target {
  final String file

  protected Target(String file) {
    this.file = file
  }

  /** `section`, `key`, `file` or `link`; `cook.lock` groups targets by it. */
  abstract String getKind()

  /** What `cook.lock` records: a map of fields, or the path for a whole file or link. */
  abstract Object getEntry()

  /** Writes `value` into the draft as this part. */
  abstract void write(Draft draft, Object value)

  /** Removes this part from the draft. */
  abstract void withdraw(Draft draft)

  /** Whether writing this target changes `other`: the same section, overlapping keys, or a whole file or link. */
  final boolean overlaps(Target other) {
    file == other.file && (whole || other.whole || other.getClass() == getClass() && sharesPart(other))
  }

  /** Whether the target is its whole file, so it overlaps every target in that file. */
  protected boolean isWhole() {
    false
  }

  /** Whether this target and `other`, of the same class and file, share a part. */
  @SuppressWarnings('UnusedMethodParameter')
  protected boolean sharesPart(Target other) {
    false
  }

  /** The text of a file this target edits in place; a symbolic link is refused, since its text would be its target. */
  protected String editable(Draft draft) {
    def target = draft.linkTarget(file)

    if (target != null) {
      throw new IllegalArgumentException("${file} is a symbolic link to ${target}; Cook writes sections and keys only into regular files")
    }

    draft.read(file)
  }

  @Override
  boolean equals(Object other) {
    other instanceof Target && other.getClass() == getClass() && ((Target) other).entry == entry
  }

  @Override
  int hashCode() {
    Objects.hash(getClass(), entry)
  }
}

/** A marked section of a text file. */
final class SectionTarget extends Target {
  /** The marker id, `<recipe>#<region>`. */
  final String id
  // A recorded section has no declared comment; withdrawing it reads the comment from its marker.
  private final String comment

  SectionTarget(String file, String id, String comment = null) {
    super(file)
    this.id = id
    this.comment = comment
  }

  String getKind() {
    'section'
  }

  Object getEntry() {
    [file: file, id: id]
  }

  void write(Draft draft, Object lines) {
    def document = blocks(editable(draft), comment)
    document.write(id, new BlockLocation(), document.render(((List) lines).join('\n')))
    draft.replace(file, document)
  }

  void withdraw(Draft draft) {
    String text = editable(draft)
    def begin = ~/.*cook:begin ${java.util.regex.Pattern.quote(id)}(?:\s.*)?/
    def marker = text?.readLines()?.find { it ==~ begin }

    if (marker != null) {
      def document = blocks(text, marker.substring(0, marker.indexOf('cook:begin ')).trim())
      document.remove(id, new BlockLocation())
      draft.replace(file, document)
    }
  }

  @Override
  @SuppressWarnings('UnusedMethodParameter')
  protected boolean sharesPart(Target other) {
    id == ((SectionTarget) other).id
  }

  @Override
  String toString() {
    "${file} section ${id}"
  }

  private BlockDocument blocks(String text, String opening) {
    if (file ==~ /(?i).*\.(md|markdown)/ || opening == '<!--') {
      return new BlockDocument(text ?: '', '<!-- ', ' -->')
    }

    new BlockDocument(text ?: '', "${opening ?: '#'} ", '')
  }
}

/** A key of a JSON, TOML or YAML file, by its path of segments. */
final class KeyTarget extends Target {
  final List<String> path

  KeyTarget(String file, List<String> path) {
    super(file)
    this.path = path
  }

  /** `json`, `toml` or `yaml` by the file's extension; `null` for any other file. */
  static String format(String file) {
    def extension = file.contains('.') ? file.substring(file.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : ''
    [json: 'json', toml: 'toml', yaml: 'yaml', yml: 'yaml'][extension]
  }

  String getKind() {
    'key'
  }

  Object getEntry() {
    [file: file, path: path]
  }

  /** Leaves a key that already holds `value` as it is, so the file keeps its formatting; a new JSON file is indented. */
  void write(Draft draft, Object value) {
    String text = editable(draft)
    def document = open(text)
    def location = new KeyLocation(path)
    def rendered = document.render(value)

    if (holds(document.read(identity, location), rendered)) {
      return
    }

    document.write(identity, location, rendered)

    if (text == null && format(file) == 'json') {
      draft.write(file, indented(new JsonSlurper().parseText(document.text), '') + '\n')
    } else {
      draft.replace(file, document)
    }
  }

  void withdraw(Draft draft) {
    def document = open(editable(draft))
    document.remove(identity, new KeyLocation(path))
    draft.replace(file, document)
  }

  @Override
  @SuppressWarnings('UnusedMethodParameter')
  protected boolean sharesPart(Target other) {
    List<String> otherPath = ((KeyTarget) other).path
    path.take(otherPath.size()) == otherPath || otherPath.take(path.size()) == path
  }

  @Override
  String toString() {
    "${file} key ${identity}"
  }

  private String getIdentity() {
    path.join('.')
  }

  private Document open(String text) {
    new Documents().open(format(file), text ?: '')
  }

  private boolean holds(String existing, String rendered) {
    if (existing == null) {
      return false
    }

    format(file) == 'json' ? new JsonSlurper().parseText(existing) == new JsonSlurper().parseText(rendered) : existing == rendered
  }

  private static String indented(Object value, String indent) {
    def inner = indent + '  '

    if (value instanceof Map && value) {
      return '{\n' + value.collect { key, item -> inner + JsonOutput.toJson(key.toString()) + ': ' + indented(item, inner) }
      .join(',\n') + '\n' + indent + '}'
    }

    if (value instanceof List && value) {
      return '[\n' + value.collect { inner + indented(it, inner) }.join(',\n') + '\n' + indent + ']'
    }

    JsonOutput.toJson(value)
  }
}

/** A whole file, with the permissions its declaration names. */
class FileTarget extends Target {
  // Permissions are written, not owned, so the lock does not record them.
  private final String mode

  FileTarget(String file, String mode = null) {
    super(file)
    this.mode = mode ?: null
  }

  String getKind() {
    'file'
  }

  Object getEntry() {
    file
  }

  void write(Draft draft, Object content) {
    draft.write(file, content.toString(), mode ? Permissions.parse(mode) : null)
  }

  void withdraw(Draft draft) {
    draft.delete(file)
  }

  @Override
  protected boolean isWhole() {
    true
  }

  @Override
  String toString() {
    file
  }
}

/** A symbolic link. */
final class LinkTarget extends FileTarget {
  LinkTarget(String file) {
    super(file)
  }

  @Override
  String getKind() {
    'link'
  }

  @Override
  void write(Draft draft, Object target) {
    draft.link(file, target.toString())
  }

  @Override
  String toString() {
    "${file} link"
  }
}
