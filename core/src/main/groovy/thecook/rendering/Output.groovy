package thecook.rendering

import thecook.composition.ComponentId

class Output {
    final ComponentId owner
    final Map declaration
    String definitionIdentity

    Output(ComponentId owner, String path, Map options) {
        this.owner = owner
        declaration = [path: path, kind: options.format ?: 'blocks', owns: options.owns ?: false,
                       mode: options.mode, adopt: options.adopt ?: false, regions: [:]]
    }
    void configure(Closure body) {
        def configured = body.rehydrate(this, body.owner, body.thisObject)
        configured.resolveStrategy = Closure.DELEGATE_FIRST
        configured.call()
    }
    void block(String key, Closure content) {
        if (!(key ==~ /[a-zA-Z0-9][a-zA-Z0-9_.:-]*/)) throw new IllegalArgumentException("Invalid region identity: ${key}")
        add(key, content)
    }
    void view(Closure renderer) { declaration.renderer = renderer }
    void text(Closure content) {
        if (!declaration.owns) throw new IllegalArgumentException('Whole-file output requires owns: true')
        declaration.kind = 'whole'
        add('content', content)
    }
    void symlink(Closure target) {
        if (!declaration.owns) throw new IllegalArgumentException('Symlink output requires owns: true')
        declaration.kind = 'symlink'
        add('content', target)
    }
    void key(List<String> path, Map options = [:], Closure content) {
        if (path.empty || path.any { !it || it.contains('\n') }) throw new IllegalArgumentException('Invalid key path')
        def identity = path.collect { it.replace('~', '~0').replace('/', '~1') }.join('/')
        add(identity, content, [path: path.toList(), adopt: options.adopt ?: false, whenPresent: options.whenPresent ?: false])
    }
    private void add(String key, Closure content, Map options = [:]) {
        if (declaration.regions.containsKey(key)) throw new IllegalArgumentException("Duplicate region: ${owner}/${key}")
        declaration.regions[key] = [supplier: content] + options
    }
    Map render() {
        def definitions = declaration.regions
        def effective = declaration
        if (declaration.renderer) {
            def view = new Output(owner, declaration.path, declaration + [format: declaration.kind])
            view.declaration.regions.putAll(declaration.regions)
            view.configure(declaration.renderer)
            definitions = view.declaration.regions
            effective = view.declaration
        }
        def regions = definitions.collectEntries { key, definition ->
            def value = definition.supplier.call()
            [(key.toString()): definition.findAll { it.key != 'supplier' } + [value: value]]
        }
        effective.findAll { !(it.key in ['regions', 'renderer']) } +
            [owner: owner.toString(), definitionIdentity: definitionIdentity, regions: regions]
    }
}
