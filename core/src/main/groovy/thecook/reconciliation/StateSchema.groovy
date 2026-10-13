package thecook.reconciliation

import thecook.composition.ContributionIdentity
import thecook.composition.ComponentId
import thecook.filesystem.Repository
import thecook.filesystem.TargetPath

class StateSchema {
  static void validate(Map state) {
    try {
      require(state.version in [1, 2, 3] && state.files instanceof Map)
      if (state.version == 3) {
        require(state.keySet() == ['version', 'files', 'applications', 'legacy', 'provenance'] as Set)
        require(state.applications instanceof Map && state.legacy instanceof Map && state.provenance instanceof Map)
        require(state.provenance.keySet().every { state.applications.containsKey(it) })
        state.provenance.each { identity, source ->
          require(source instanceof Map && source.keySet() == ['recipe', 'inputs'] as Set)
          require(source.recipe instanceof String && source.inputs instanceof Map)
          require(source.inputs.every { key, value -> key instanceof String && value instanceof String })
        }
      }
      if (state.applications != null) {
        require(state.version in [2, 3] && state.applications instanceof Map)
        state.applications.each { identity, paths ->
          require(identity ==~ /[a-zA-Z0-9][a-zA-Z0-9_.:-]*\/[a-zA-Z0-9][a-zA-Z0-9_.:-]*/ && paths instanceof Map)
          paths.each { path, addresses ->
            TargetPath.of(path.toString())
            require(addresses instanceof List && addresses.every { it instanceof String && it })
          }
        }
      }
      state.files.each { path, file ->
        TargetPath.of(path.toString())
        require(file instanceof Map && file.kind in ['whole', 'blocks', 'json', 'toml', 'yaml', 'symlink'])
        if (file.kind in ['whole', 'symlink'] || state.version == 1) ComponentId.of(file.owner)
        if (file.kind == 'symlink') {
          linkTarget(file.rendered); snapshot(file.original)
          require(!file.original.exists || file.original.kind == 'symlink')
          return
        }
        if (file.kind == 'whole') {
          require(file.rendered instanceof String); snapshot(file.original); return
        }
        require(file.regions instanceof Map && file.metadata instanceof Map && file.created instanceof Boolean)
        file.regions.each { key, region ->
          require(region instanceof Map && region.rendered instanceof String && region.definition instanceof Map)
          if (state.version in [2, 3]) {
            def address = ContributionIdentity.parse(key.toString())
            require(address.instance.value == region.owner)
          }
          if (region.definitionIdentity != null) ComponentId.of(region.definitionIdentity)
          require(region.original == null || region.original instanceof String)
          if (file.kind != 'blocks') require(region.definition.path instanceof List && !region.definition.path.isEmpty() && region.definition.path.every { it instanceof String && it })
        }
      }
      if (state.version == 3) {
        def membership = [:].withDefault { [] }
        (state.applications.values().toList() + [state.legacy]).each { paths ->
          require(paths instanceof Map)
          paths.each { path, addresses ->
            TargetPath.of(path.toString())
            require(addresses instanceof List && addresses.every { it instanceof String && it })
            membership[path].addAll(addresses)
          }
        }
        state.files.each { path, file ->
          def addresses = file.kind in ['whole', 'symlink'] ? ['*'] : file.regions.keySet().toList()
          require(membership[path].size() == membership[path].toSet().size() && membership[path].toSet() == addresses.toSet())
        }
        require(membership.keySet() == state.files.keySet())
      }
    } catch (Exception error) {
      throw new IllegalArgumentException('Unsupported or malformed Cook state', error)
    }
  }
  /** Converts legacy records in memory; the transaction persists them after successful application. */
  static Map current(Map state) {
    validate(state)
    if (state.version == 3) return state
    def files = state.files.collectEntries { path, file ->
      if (state.version == 2) return [(path): file]
      if (file.kind in ['whole', 'symlink']) return [(path): file]
      def regions = file.regions.collectEntries { key, region ->
        [(ContributionIdentity.of(file.owner, key.toString()).toString()): region + [owner: file.owner]]
      }
      [(path): file.findAll { it.key != 'owner' } + [regions: regions]]
    }
    def applications = state.applications ?: [:]
    def legacy = files.collectEntries { path, file ->
      def addresses = file.kind in ['whole', 'symlink'] ? ['*'] : file.regions.keySet().toList()
      def claimed = applications.values().collectMany { it[path] ?: [] }.toSet()
      def unknown = addresses.findAll { !claimed.contains(it) }
      unknown ? [(path): unknown] : [:]
    }
    def migrated = [version: 3, files: files, applications: applications, legacy: legacy, provenance: [:]]
    validate(migrated)
    migrated
  }
  static Map sorted(Map value) {
    def result = value.keySet().sort().collectEntries { key ->
      def item = value[key]
      [(key): item instanceof Map ? sorted(item) : item instanceof List ? item.collect { it instanceof Map ? sorted(it) : it } : item]
    }
    if (value.version == 3 && value.files instanceof Map) {
      result.applications.each { id, paths ->
        paths.each { path, addresses ->
          paths[path] = addresses.toList().sort()
        }
      }
      result.legacy.each { path, addresses -> result.legacy[path] = addresses.toList().sort() }
    }
    result
  }
  static void snapshot(Map snapshot) {
    require(snapshot instanceof Map && snapshot.exists instanceof Boolean)
    require(snapshot.kind == null || snapshot.kind == 'symlink')
    if (!snapshot.exists) {
      require(snapshot.hash == null && snapshot.content == null && snapshot.mode == null); return
    }
    require(snapshot.content instanceof String && snapshot.hash == Repository.digest(snapshot.content.getBytes('UTF-8')))
    require(snapshot.mode == null || snapshot.mode ==~ /[r-][w-][x-][r-][w-][x-][r-][w-][x-]/)
    if (snapshot.kind == 'symlink') {
      linkTarget(snapshot.content); require(snapshot.mode == null)
    }
  }
  static void linkTarget(Object value) {
    require(value instanceof String && value && !value.toCharArray().any { it < 32 || it == 127 })
  }
  private static void require(boolean condition) {
    if (!condition) {
      throw new IllegalArgumentException('Invalid state shape')
    }
  }
}
