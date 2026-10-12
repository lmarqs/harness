package cook

import org.gradle.api.GradleException
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.nodes.*

// Parse to locate fields, then edit their source spans. No YAML reserialization.
// Deliberately limited to ordinary block mappings; unknown shapes fail the probe.
class YamlEdits {
    String text
    Node root

    YamlEdits(String text) {
        this.text = text
        root = new Yaml().compose(new StringReader(text))
        validate(root)
    }

    static void validate(Node node) {
        if (node.anchor) throw new GradleException('YAML anchors are outside the probe scope')
        if (node instanceof MappingNode) {
            def keys = node.value.collect { it.keyNode.value }
            if (keys.contains('<<') || keys.unique(false).size() != keys.size()) throw new GradleException('YAML merge keys or duplicate keys are outside the probe scope')
            node.value.each { validate(it.valueNode) }
        } else if (node instanceof SequenceNode) node.value.each { validate(it) }
    }

    Node at(List path) {
        Node node = root
        for (part in path) {
            if (node == null || node.tag == Tag.NULL) return null
            if (node instanceof SequenceNode && part instanceof Integer) node = node.value[part]
            else if (node instanceof MappingNode) node = node.value.find { it.keyNode.value == part }?.valueNode
            else throw new GradleException("Expected a YAML mapping at ${path}")
        }
        node
    }

    List<String> keys(List path) {
        def node = at(path)
        if (node == null || node.tag == Tag.NULL) return []
        if (!(node instanceof MappingNode)) throw new GradleException("Expected mapping at ${path}")
        node.value.collect { it.keyNode.value }
    }

    String scalar(List path) {
        def node = at(path)
        if (node == null) return null
        if (!(node instanceof ScalarNode)) throw new GradleException("Expected scalar at ${path}")
        node.value
    }

    int lineStart(int index) { text.lastIndexOf('\n', Math.max(0, index - 1)) + 1 }

    void splice(int start, int end, String replacement) {
        text = text.substring(0, start) + replacement + text.substring(end)
        root = new Yaml().compose(new StringReader(text))
        validate(root)
    }

    void setScalar(List path, String value) {
        def node = at(path)
        if (node == null) throw new GradleException("Missing YAML field ${path}")
        splice(node.startMark.index, node.endMark.index, value)
    }

    List<Integer> bounds(List path) {
        def parent = at(path.dropRight(1))
        if (!(parent instanceof MappingNode)) return null
        def entry = parent?.value?.find { it.keyNode.value == path[-1] }
        if (!entry) return null
        int start = lineStart(entry.keyNode.startMark.index)
        def endMark = entry.valueNode.endMark
        int end = endMark.column == 0 ? endMark.index : text.indexOf('\n', endMark.index) + 1
        if (end == 0) end = text.length()
        // Block mappings may end at the next sibling's indentation.
        if (text.substring(lineStart(endMark.index), endMark.index).trim().isEmpty()) end = lineStart(endMark.index)
        while (end > start) {
            int last = lineStart(end - 1)
            def line = text.substring(last, end).trim()
            if (line && !line.startsWith('#')) break
            end = last
        }
        [start, end]
    }

    void remove(List path) {
        def range = bounds(path)
        if (range != null) splice(range[0], range[1], '')
    }

    void prepend(List path, String block) {
        def node = at(path)
        if (!(node instanceof MappingNode) || node.value.isEmpty() || node.flowStyle == FlowStyle.FLOW) {
            throw new GradleException("Expected nonempty block mapping at ${path}")
        }
        def mark = node.value[0].keyNode.startMark
        def indent = ' ' * mark.column
        splice(lineStart(mark.index), lineStart(mark.index), block.readLines().collect { indent + it + '\n' }.join(''))
    }

    void replaceMapping(List path, String block) {
        def parent = at(path.dropRight(1))
        def entry = parent.value.find { it.keyNode.value == path[-1] }
        int indent = entry.keyNode.startMark.column
        def range = bounds(path)
        def replacement = (' ' * indent) + path[-1] + ':\n' + block.readLines().collect { (' ' * (indent + 2)) + it + '\n' }.join('')
        splice(range[0], range[1], replacement)
    }
}
