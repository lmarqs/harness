package thecook.composition

import thecook.filesystem.Repository
import thecook.reconciliation.Reconciler
import thecook.rendering.RenderTree

class Kitchen {
    final Repository repository
    final Map composition = [components: [:], roots: new LinkedHashSet()]

    Kitchen(File directory) { repository = new Repository(directory) }
    Component ingredient(String id, Closure configure = {}) { component(id, 'ingredient', configure) }
    Component target(String id, Closure configure = {}) { component(id, 'target', configure) }
    Component station(String id, Closure configure = {}) { component(id, 'station', configure) }
    Component recipe(String id, Closure configure = {}) { component(id, 'recipe', configure) }
    private Component component(String name, String kind, Closure configure) {
        def id = ComponentId.of(name)
        if (composition.components.containsKey(name)) throw new IllegalArgumentException("Duplicate component: ${name}")
        def result = new Component(this, id, kind)
        composition.components[name] = result
        result.call(configure)
    }
    void mount(Component component) { ensureMember(component); composition.roots.add(component) }
    void unmount(Component component) { composition.roots.remove(component) }
    private void ensureMember(Component component) {
        if (!component.kitchen.is(this)) throw new IllegalArgumentException('Cannot connect components from another kitchen')
    }
    List<Component> mounted() {
        def found = new LinkedHashSet<Component>()
        composition.roots.each { visit(it, found, []) }
        found.toList()
    }
    private void visit(Component component, Set<Component> found, List<Component> ancestors) {
        ensureMember(component)
        if (ancestors.contains(component)) throw new IllegalArgumentException("Cyclic composition: ${(ancestors + component)*.id}")
        if (!found.add(component)) return
        component.definition.children.each { visit(it, found, ancestors + component) }
    }
    RenderTree render() { RenderTree.from(mounted()) }
    def plan() { new Reconciler().plan(repository, render()) }
    def eject() { new Reconciler().plan(repository, new RenderTree([]), true) }
}
