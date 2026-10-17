package thecook.formats

import thecook.rendering.RegionLocation
import thecook.rendering.KeyLocation

import groovy.json.JsonOutput
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag

class YamlDocument implements Document {
  String text
  final List<List<String>> createdParents = []
  YamlDocument(String text) {
    this.text = text; root()
  }
  boolean isEmpty() {
    !root() || (root() instanceof MappingNode && root().value.isEmpty())
  }
  private int index(int codePoints) {
    text.offsetByCodePoints(0, codePoints)
  }
  private List<Integer> valueBounds(Node node) {
    int start = index(node.startMark.index)
    int end = index(node.endMark.index)

    if (node.endMark.column == 0 && end > start && text.charAt(end - 1) == '\n' as char) {
      end -= end > 1 && text.charAt(end - 2) == '\r' as char ? 2 : 1
    }

    [start, end]
  }
  private Node root() {
    try {
      def node = new Yaml().compose(new StringReader(text))
      validate(node)

      if (node && !(node instanceof MappingNode)) {
        throw new IllegalArgumentException('YAML requires a root mapping')
      }

      node
    } catch (org.yaml.snakeyaml.error.YAMLException error) {
      throw new IllegalArgumentException('YAML: ' + error.message, error)
    }
  }
  private static void validate(Node node) {
    if (!node) {
      return
    }

    if (node.anchor) {
      throw new IllegalArgumentException('YAML anchors are unsupported for shared edits')
    }

    if (node instanceof MappingNode) {
      def keys = node.value.collect { tuple ->
        if (!(tuple.keyNode instanceof ScalarNode)) {
          throw new IllegalArgumentException('YAML requires scalar mapping keys')
        }

        tuple.keyNode.value
      }

      if (keys.contains('<<') || keys.unique(false).size() != keys.size()) {
        throw new IllegalArgumentException('YAML merges or duplicate keys are unsupported')
      }

      node.value.each { validate(it.valueNode) }
    }

    if (node instanceof SequenceNode) {
      node.value.each { validate(it) }
    }
  }
  private Node at(List<String> path) {
    def node = root()

    for (part in path) {
      if (!node) {
        return null
      }

      if (!(node instanceof MappingNode)) {
        throw new IllegalArgumentException("Expected YAML mapping before ${part}")
      }

      node = node.value.find { it.keyNode.value == part }?.valueNode
    }

    node
  }
  @Override
  String read(String id, RegionLocation location) {
    def node = at(((KeyLocation) location).path)

    if (!node) {
      return null
    }

    def range = valueBounds(node)
    text.substring(range[0], range[1])
  }
  String render(Object value) {
    JsonOutput.toJson(value)
  }
  @Override
  void write(String id, RegionLocation location, String content) {
    def path = ((KeyLocation) location).path
    ensureParents(path.dropRight(1))
    def node = at(path)

    if (node) {
      def range = valueBounds(node); splice(range[0], range[1], content); return
    }

    insert(path, content)
  }
  private void ensureParents(List<String> path) {
    for (int count = 1; count <= path.size(); count++) {
      def parent = path.take(count)

      if (!at(parent)) {
        insert(parent, '{}'); createdParents.add(parent)
      }

      if (!(at(parent) instanceof MappingNode)) {
        throw new IllegalArgumentException("Expected YAML mapping at ${parent}")
      }
    }
  }
  private void insert(List<String> path, String content) {
    def parentPath = path.dropRight(1)
    def parent = at(parentPath)
    def ending = text.contains('\r\n') ? '\r\n' : '\n'
    int indent = parentPath.size() * 2
    def entry = (' ' * indent) + JsonOutput.toJson(path[-1]) + ': ' + content + ending

    if (!parent) {
      text += (text && !text.endsWith('\n') ? ending : '') + entry; root(); return
    }

    if (parent.flowStyle == FlowStyle.FLOW) {
      if (!parent.value.isEmpty()) {
        throw new IllegalArgumentException('YAML flow mapping insertion is unsupported')
      }

      int start = index(parent.startMark.index)
      int end = index(parent.endMark.index)
      // Reuse the existing line ending after an empty flow mapping.
      String replacement = ending + entry

      if (text.substring(end).startsWith(ending)) {
        replacement = replacement.substring(0, replacement.length() - ending.length())
      }

      splice(start, end, replacement)
      return
    }

    def mark = parent.value[0].keyNode.startMark
    int start = text.lastIndexOf('\n', index(mark.index) - 1) + 1
    entry = (' ' * mark.column) + JsonOutput.toJson(path[-1]) + ': ' + content + ending
    splice(start, start, entry)
  }
  @Override
  void remove(String id, RegionLocation location) {
    deleteKey(((KeyLocation) location).path)
    createdParents.sort { -it.size() }.toList().each { path ->
      def node = at(path)

      if ((node instanceof MappingNode && node.value.isEmpty()) || node?.tag == Tag.NULL) {
        deleteKey(path); createdParents.remove(path)
      }
    }
  }
  private void deleteKey(List<String> path) {
    def parent = at(path.dropRight(1))
    def tuple = parent?.value?.find { it.keyNode.value == path[-1] }

    if (!tuple) {
      return
    }

    if (parent.flowStyle == FlowStyle.FLOW) {
      throw new IllegalArgumentException('YAML flow mapping removal is unsupported')
    }

    int start = text.lastIndexOf('\n', index(tuple.keyNode.startMark.index) - 1) + 1
    int end = index(tuple.valueNode.endMark.index)

    if (tuple.valueNode.endMark.column != 0) {
      end = text.indexOf('\n', end); end = end < 0 ? text.length() : end + 1
    }

    splice(start, end, '')
  }
  private void splice(int start, int end, String content) {
    text = text.substring(0, start) + content + text.substring(end); root()
  }
}
