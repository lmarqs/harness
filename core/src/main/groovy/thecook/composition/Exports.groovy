package thecook.composition

class Exports {
  final Map<String, Deferred> values
  final String prefix

  Exports(Map<String, Deferred> values, String prefix = '') {
    this.values = values; this.prefix = prefix
  }
  Object propertyMissing(String name) {
    def path = prefix ? "${prefix}.${name}".toString() : name
    if (values.containsKey(path)) return values[path]
    if (values.keySet().any { it.startsWith(path + '.') }) return new Exports(values, path)
    throw new IllegalArgumentException("Unknown export: ${path}")
  }
}
