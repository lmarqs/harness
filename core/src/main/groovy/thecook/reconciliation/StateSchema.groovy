package thecook.reconciliation

import thecook.composition.ContributionIdentity
import thecook.composition.ComponentId
import thecook.filesystem.Repository
import thecook.filesystem.TargetPath

class StateSchema {
  static void validate(Map state) {
    try {
      require(state.version in [1, 2] && state.files instanceof Map)
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
          if (state.version == 2) {
            def address = ContributionIdentity.parse(key.toString())
            require(address.instance.value == region.owner)
          }
          if (region.definitionIdentity != null) ComponentId.of(region.definitionIdentity)
          require(region.original == null || region.original instanceof String)
          if (file.kind != 'blocks') require(region.definition.path instanceof List && !region.definition.path.isEmpty() && region.definition.path.every { it instanceof String && it })
        }
      }
    } catch (Exception error) {
      throw new IllegalArgumentException('Unsupported or malformed Cook state', error)
    }
  }
  /** Converts legacy records in memory; the transaction persists them after successful application. */
  static Map current(Map state) {
    validate(state)
    if (state.version == 2) return state
    def files = state.files.collectEntries { path, file ->
      if (file.kind in ['whole', 'symlink']) return [(path): file]
      def regions = file.regions.collectEntries { key, region ->
        [(ContributionIdentity.of(file.owner, key.toString()).toString()): region + [owner: file.owner]]
      }
      [(path): file.findAll { it.key != 'owner' } + [regions: regions]]
    }
    [version: 2, files: files]
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
    if (!condition) throw new IllegalArgumentException('Invalid state shape')
  }
}
