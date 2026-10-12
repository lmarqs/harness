package thecook.composition

class Connections {
    final List sources
    Connections(List sources = []) { this.sources = sources }
    Connections plus(Object source) { new Connections(sources + [source]) }
    void from(Object source) { sources.add(source) }
    List getValue() {
        sources.collectMany { source ->
            def resolved = source instanceof Deferred ? source.value : source
            if (!(resolved instanceof Collection)) throw new IllegalArgumentException('A path connection must export a collection')
            resolved.toList()
        }.unique()
    }
}
