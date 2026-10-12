// A file writer resolves named contributions and rejects ambiguous values.
return { Collection contributions, String category ->
    def merged = new LinkedHashMap()
    contributions.each { contribution ->
        if (!(contribution instanceof Map)) throw new IllegalArgumentException("${category} must export maps")
        contribution.each { name, value ->
            def key = name.toString()
            if (merged.containsKey(key) && merged[key] != value)
                throw new IllegalArgumentException("Conflicting ${category}: ${key}")
            merged[key] = value
        }
    }
    merged
}
