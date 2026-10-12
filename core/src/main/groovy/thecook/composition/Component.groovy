package thecook.composition

import thecook.rendering.Output

class Component {
    final Kitchen kitchen
    final Map definition

    Component(Kitchen kitchen, ComponentId id, String kind) {
        this.kitchen = kitchen
        definition = [id: id, kind: kind, parameters: [:], exports: [:], outputs: [], renderers: [], children: [], requires: [], checks: []]
    }
    ComponentId getId() { definition.id }
    Component call(Closure configure) { evaluate(configure); this }
    Object evaluate(Closure closure) {
        def configured = closure.rehydrate(this, closure.owner, closure.thisObject)
        configured.resolveStrategy = Closure.DELEGATE_FIRST
        configured.call()
    }
    void export(String path, Closure supplier) {
        if (definition.exports.containsKey(path)) throw new IllegalArgumentException("Duplicate export: ${id}.${path}")
        definition.exports[path] = new Deferred("${id}.${path}", { evaluate(supplier) })
    }
    Object propertyMissing(String name) {
        if (definition.parameters.containsKey(name)) return definition.parameters[name]
        new Exports(definition.exports).getProperty(name)
    }
    void propertyMissing(String name, Object value) { definition.parameters[name] = value }
    void input(String name) { definition.parameters[name] = new Connections() }
    void requires(Component dependency) { definition.requires.add(dependency) }
    void includes(Component child) { definition.children.add(child) }
    void validate(Closure check) { definition.checks.add(check) }
    void view(Closure renderer) { definition.renderers.add(renderer) }
    List<Output> renderOutputs() {
        def view = new Component(kitchen, id, definition.kind)
        view.definition.parameters.putAll(definition.parameters)
        view.definition.exports.putAll(definition.exports)
        definition.renderers.each { view.evaluate(it) }
        definition.outputs + view.definition.outputs
    }
    Output output(String path, Map options = [:], Closure configure) {
        def output = new Output(id, path, options)
        output.configure(configure)
        definition.outputs.add(output)
        output
    }
    void mount() { kitchen.mount(this) }
    void unmount() { kitchen.unmount(this) }
}
