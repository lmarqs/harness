package thecook.formats

import thecook.rendering.RegionLocation
import thecook.rendering.KeyLocation

import groovy.json.JsonOutput

class JsonDocument implements Document {
  String text
  final List<List<String>> createdParents = []
  JsonDocument(String text) {
    this.text = text ?: '{}\n'; root()
  }
  void seed(Map metadata) {
    createdParents.addAll(metadata.createdParents ?: [])
  }
  Map getMetadata() {
    [createdParents: createdParents]
  }
  boolean isEmpty() {
    root().members.isEmpty()
  }
  private Map root() {
    new JsonSyntax(text).parse()
  }
  private Map at(List<String> path) {
    def node = root()
    for (part in path) {
      if (node.kind != 'object') throw new IllegalArgumentException("Expected JSON object before ${part}")
      node = node.members[part]?.value
      if (!node) return null
    }
    node
  }
  String read(String id, RegionLocation location) {
    def node = at(((KeyLocation) location).path)
    node ? text.substring(node.start, node.end) : null
  }
  String render(Object value) {
    JsonOutput.toJson(value)
  }
  void write(String id, RegionLocation location, String content) {
    def path = ((KeyLocation) location).path
    ensureParents(path.dropRight(1))
    def node = at(path)
    if (node) {
      splice(node.start, node.end, content); return
    }
    insert(path, content)
  }
  private void ensureParents(List<String> path) {
    for (int count = 1; count <= path.size(); count++) {
      def parent = path.take(count)
      if (!at(parent)) {
        insert(parent, '{}'); createdParents.add(parent)
      }
      if (at(parent).kind != 'object') throw new IllegalArgumentException("Expected JSON object at ${parent}")
    }
  }
  private void insert(List<String> path, String content) {
    def parent = at(path.dropRight(1))
    def members = parent.members.values().toList()
    int offset = members ? members[-1].value.end : parent.start + 1
    def entry = (members ? ', ' : '') + JsonOutput.toJson(path[-1]) + ':' + content
    splice(offset, offset, entry)
  }
  void remove(String id, RegionLocation location) {
    deleteKey(((KeyLocation) location).path)
    createdParents.sort { -it.size() }.toList().each { path ->
      def node = at(path)
      if (node?.kind == 'object' && node.members.isEmpty()) {
        deleteKey(path); createdParents.remove(path)
      }
    }
  }
  private void deleteKey(List<String> path) {
    def parent = at(path.dropRight(1))
    if (!parent) return
      def entry = parent.members[path[-1]]
    if (!entry) return
      def members = parent.members.values().toList()
    int index = members.indexOf(entry)
    if (index > 0) {
      splice(members[index - 1].value.end, entry.value.end, ''); return
    }
    int end = members.size() > 1 ? members[1].keyStart : entry.value.end
    splice(entry.keyStart, end, '')
  }
  void release(String id, RegionLocation location) {}
  private void splice(int start, int end, String replacement) {
    text = text.substring(0, start) + replacement + text.substring(end); root()
  }
}
