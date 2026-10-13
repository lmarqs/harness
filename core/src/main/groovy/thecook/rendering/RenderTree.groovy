package thecook.rendering

import thecook.composition.Component
import thecook.composition.ContributionIdentity
import thecook.filesystem.TargetPath

class RenderTree {
    final List<Map> outputs
    RenderTree(List<Map> outputs) { this.outputs = outputs }

    static RenderTree from(List<Component> components) {
        validateDependencies(components)
        components.each { component -> component.definition.checks.each { component.evaluate(it) } }
        def outputs = components.collectMany { it.renderOutputs().collect { it.render() } }
        def paths = [:]
        outputs.each { output ->
            def path = TargetPath.of(output.path).value
            def canonical = path.toLowerCase(Locale.ROOT)
            if (paths.containsKey(canonical) && paths[canonical].path != path)
                throw new IllegalArgumentException("Multiple writers with case collision: ${paths[canonical].path}, ${path}")
            def combined = paths[canonical]
            boolean exclusive = output.kind in ['whole', 'symlink']
            if (combined && (exclusive || combined.kind in ['whole', 'symlink'] || combined.kind != output.kind))
                throw new IllegalArgumentException("Multiple writers with incompatible ownership or format: ${path}")
            if (!combined) {
                combined = exclusive ? output + [path: path] : [path: path, kind: output.kind, regions: [:]]
                paths[canonical] = combined
            }
            if (!exclusive) output.regions.each { key, region ->
                String address = ContributionIdentity.of(output.owner, key.toString()).toString()
                if (combined.regions.containsKey(address))
                    throw new IllegalArgumentException("Duplicate contribution: ${path} ${address}")
                combined.regions[address] = region + [owner: output.owner, definitionIdentity: output.definitionIdentity]
            }
        }
        paths.keySet().each { path ->
            if (paths.keySet().any { it != path && it.startsWith(path + '/') })
                throw new IllegalArgumentException("File-directory collision: ${path}")
        }
        paths.values().each { validateKeys(it) }
        new RenderTree(paths.values().toList().sort { it.path })
    }
    private static void validateKeys(Map output) {
        def keys = output.regions.findAll { it.value.path }
        keys.each { address, region ->
            def conflict = keys.find { other, candidate ->
                other != address && (candidate.path.take(region.path.size()) == region.path ||
                                     region.path.take(candidate.path.size()) == candidate.path)
            }
            if (conflict)
                throw new IllegalArgumentException("Overlapping keys: ${output.path}: ${address} conflicts with ${conflict.key}")
        }
    }
    private static void validateDependencies(List<Component> components) {
        def slots = [:]
        components.each { component ->
            component.definition.requires.each { dependency ->
                if (!components.contains(dependency)) throw new IllegalArgumentException("${component.id} requires mounted ${dependency.id}")
            }
            def slot = component.definition.parameters.slot
            if (slot && slots.containsKey(slot)) throw new IllegalArgumentException("Multiple components in slot ${slot}")
            if (slot) slots[slot] = component
        }
    }
}
