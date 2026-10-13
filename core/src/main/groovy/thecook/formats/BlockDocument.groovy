package thecook.formats

import thecook.rendering.RegionLocation

class BlockDocument implements Document {
  String text
  BlockDocument(String text) {
    this.text = text; validateMarkers()
  }
  private final Set<String> separators = new LinkedHashSet<>()
  void seed(Map metadata) {
    separators.addAll(metadata.separators ?: [])
  }
  Map getMetadata() {
    separators ? [separators: separators.toList().sort()] : [:]
  }
  boolean isEmpty() {
    text.isEmpty()
  }
  private String newline() {
    text.contains('\r\n') ? '\r\n' : '\n'
  }
  private static String begin(String id) {
    "# cook:begin ${id}"
  }
  private static String end(String id) {
    "# cook:end ${id}"
  }
  private void validateMarkers() {
    String open = null
    def seen = new HashSet()
    text.readLines().each { line ->
      if (line.startsWith('# cook:begin ')) {
        def id = line.substring(13)
        if (!(id ==~ /[a-zA-Z0-9][a-zA-Z0-9_.:\/-]*/)) throw new IllegalArgumentException('Invalid Cook marker identity')
        if (open || !seen.add(id)) throw new IllegalArgumentException('Nested or duplicate Cook markers')
        open = id
      }
      if (line.startsWith('# cook:end ')) {
        if (open != line.substring(11)) throw new IllegalArgumentException('Mismatched Cook markers')
        open = null
      }
    }
    if (open) throw new IllegalArgumentException('Unterminated Cook marker')
  }
  private List<Integer> bounds(String id) {
    def first = java.util.regex.Pattern.compile('(?m)^' + java.util.regex.Pattern.quote(begin(id)) + '\\r?$').matcher(text)
    if (!first.find()) return null
    def last = java.util.regex.Pattern.compile('(?m)^' + java.util.regex.Pattern.quote(end(id)) + '\\r?$').matcher(text)
    if (!last.find(first.end())) throw new IllegalArgumentException('Missing Cook end marker')
    def finish = text.indexOf('\n', last.end())
    [first.start(), finish < 0 ? text.length() : finish + 1]
  }
  String read(String id, RegionLocation location) {
    def range = bounds(id)
    if (!range) return null
    def first = text.indexOf('\n', range[0]) + 1
    def marker = java.util.regex.Pattern.compile('(?m)^' + java.util.regex.Pattern.quote(end(id)) + '\\r?$').matcher(text)
    marker.find(first)
    def last = marker.start()
    text.substring(first, last)
  }
  String render(Object value) {
    def result = value instanceof Collection ? value.collect { it.toString() }.unique().join('\n') : value.toString()
    if (result.contains('# cook:begin ') || result.contains('# cook:end ')) throw new IllegalArgumentException('Reserved marker in rendered content')
    result = result.replace('\r\n', '\n').replace('\n', newline())
    result ? result.replaceAll(/(?:\r?\n)+$/, '') + newline() : ''
  }
  void write(String id, RegionLocation location, String content) {
    def range = bounds(id)
    def block = begin(id) + newline() + content + end(id) + newline()
    if (range) {
      splice(range, block); return
    }
    boolean separator = text && !text.endsWith('\n')
    if (separator) separators.add(id)
    text += (separator ? newline() : '') + block
  }
  void remove(String id, RegionLocation location) {
    def range = bounds(id)
    if (!range) return
      boolean recorded = separators.remove(id)
    boolean separator = recorded
    if (separator && range[0] > 0) {
      def following = text.substring(range[1])
      if (following.startsWith('# cook:begin ')) {
        // The newline still separates handwritten text from the next managed block.
        separators.add(following.readLines().first().substring(13))
      } else if (!following) {
        range[0] -= range[0] >= 2 && text.charAt(range[0] - 2) == '\r' as char ? 2 : 1
      }
    }
    splice(range, '')
  }
  void release(String id, RegionLocation location) {
    def content = read(id, location)
    def range = bounds(id)
    separators.remove(id)
    if (range) splice(range, content)
  }
  private void splice(List<Integer> range, String replacement) {
    text = text.substring(0, range[0]) + replacement + text.substring(range[1])
  }
}
