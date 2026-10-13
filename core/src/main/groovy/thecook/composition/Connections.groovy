package thecook.composition

class Connections {
  final List sources
  Connections(List sources = []) {
    this.sources = sources
  }
  Connections plus(Object source) {
    new Connections(sources + [source])
  }
  void from(Object source) {
    sources.add(source)
  }
  Map merge(String category) {
    def merged = new LinkedHashMap()
    value.each { contribution ->
      if (!(contribution instanceof Map)) throw new IllegalArgumentException("${category} must export maps")
      contribution.each { name, entry ->
        def key = name.toString()
        if (merged.containsKey(key) && merged[key] != entry)
          throw new IllegalArgumentException("Conflicting ${category}: ${key}")
        merged[key] = entry
      }
    }
    merged
  }
  List getValue() {
    sources.collectMany { source ->
      def resolved = source instanceof Deferred ? source.value : source
      if (!(resolved instanceof Collection)) throw new IllegalArgumentException('A path connection must export a collection')
      resolved.toList()
    }.unique()
  }
}
