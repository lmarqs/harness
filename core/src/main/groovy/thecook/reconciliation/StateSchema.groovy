package thecook.reconciliation

import thecook.composition.ComponentId
import thecook.filesystem.Repository
import thecook.filesystem.TargetPath

class StateSchema {
    static void validate(Map state) {
        try {
            require(state.version == 1 && state.files instanceof Map)
            state.files.each { path, file ->
                TargetPath.of(path.toString()); ComponentId.of(file.owner)
                require(file instanceof Map && file.kind in ['whole', 'blocks', 'json', 'toml', 'yaml', 'symlink'])
                if (file.kind == 'symlink') {
                    linkTarget(file.rendered); snapshot(file.original)
                    require(!file.original.exists || file.original.kind == 'symlink')
                    return
                }
                if (file.kind == 'whole') { require(file.rendered instanceof String); snapshot(file.original); return }
                require(file.regions instanceof Map && file.metadata instanceof Map && file.created instanceof Boolean)
                file.regions.each { key, region ->
                    require(region instanceof Map && region.rendered instanceof String && region.definition instanceof Map)
                    require(region.original == null || region.original instanceof String)
                    if (file.kind != 'blocks') require(region.definition.path instanceof List && !region.definition.path.isEmpty() && region.definition.path.every { it instanceof String && it })
                }
            }
        } catch (Exception error) { throw new IllegalArgumentException('Unsupported or malformed Cook state', error) }
    }
    static void snapshot(Map snapshot) {
        require(snapshot instanceof Map && snapshot.exists instanceof Boolean)
        require(snapshot.kind == null || snapshot.kind == 'symlink')
        if (!snapshot.exists) { require(snapshot.hash == null && snapshot.content == null && snapshot.mode == null); return }
        require(snapshot.content instanceof String && snapshot.hash == Repository.digest(snapshot.content.getBytes('UTF-8')))
        require(snapshot.mode == null || snapshot.mode ==~ /[r-][w-][x-][r-][w-][x-][r-][w-][x-]/)
        if (snapshot.kind == 'symlink') { linkTarget(snapshot.content); require(snapshot.mode == null) }
    }
    static void linkTarget(Object value) {
        require(value instanceof String && value && !value.toCharArray().any { it < 32 || it == 127 })
    }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException('Invalid state shape') }
}
