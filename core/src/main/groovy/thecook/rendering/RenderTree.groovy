package thecook.rendering

import thecook.composition.Component
import thecook.filesystem.TargetPath

class RenderTree {
    final List<Map> outputs
    RenderTree(List<Map> outputs) { this.outputs = outputs }

    static RenderTree from(List<Component> components) {
        validateDependencies(components)
        components.each { component -> component.definition.checks.each { component.evaluate(it) } }
        def outputs = components.collectMany { it.renderOutputs().collect { it.render() } }
        def paths = new HashSet<String>()
        outputs.each { output ->
            def path = TargetPath.of(output.path)
            if (!paths.add(path.value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Multiple writers for ${path}")
            output.path = path.value
            validateKeys(output)
        }
        paths.each { path ->
            if (paths.any { it != path && it.startsWith(path + '/') })
                throw new IllegalArgumentException("File-directory collision: ${path}")
        }
        new RenderTree(outputs.sort { it.path })
    }
    private static void validateKeys(Map output) {
        def paths = output.regions.values().findAll { it.path }.collect { it.path }
        paths.each { path ->
            if (paths.any { it.size() > path.size() && it.take(path.size()) == path })
                throw new IllegalArgumentException("Overlapping keys: ${path}")
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
