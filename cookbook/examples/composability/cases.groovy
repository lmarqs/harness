import thecook.composition.Kitchen

def verify = new GroovyShell(getClass().classLoader).evaluate(new File(System.getProperty('thecook.cookbook', 'cookbook'), 'examples/verify.groovy'))
verify('composability') { book, root ->
    // Producers stay lazy. Mounting Git consumes exports without mounting Node.
    def kitchen = new Kitchen(root)
    def node = book.ingredient('node', kitchen, [version: '22'])
    def npm = book.ingredient('npm', kitchen)
    def git = book.ingredient('git', kitchen)
    git.ignore += node.files.ephemeral
    git.ignore += npm.files.ephemeral
    git.mount()
    kitchen.plan().apply()
    assert new File(root, '.gitignore').text.contains('node_modules/')
    assert !new File(root, 'package.json').exists()
    assert kitchen.plan().changes.empty

    // Shared structured output combines different keys and rejects conflicting pins.
    def conflictRoot = new File(root, 'conflict')
    conflictRoot.mkdirs()
    def conflict = new Kitchen(conflictRoot)
    def mise = book.ingredient('mise', conflict)
    mise.tools += [[node: '22'], [node: '24']]
    mise.mount()
    try { conflict.plan(); assert false: 'Two Node versions must conflict' }
    catch (IllegalArgumentException expected) { assert expected.message.contains('Conflicting mise tools: node') }
    assert !new File(conflictRoot, 'mise.toml').exists()

    def managers = new Kitchen(conflictRoot)
    book.ingredient('npm', managers).mount()
    book.ingredient('yarn', managers).mount()
    try { managers.plan(); assert false: 'Package manager selection must be exclusive' }
    catch (IllegalArgumentException expected) { assert expected.message.contains('node-package-manager') }

    def missingDependency = new Kitchen(conflictRoot)
    def runtime = book.ingredient('node', missingDependency)
    book.ingredient('npm', missingDependency, [node: runtime]).mount()
    try { missingDependency.plan(); assert false: 'A declared dependency must be mounted' }
    catch (IllegalArgumentException expected) { assert expected.message.contains('requires mounted node') }

    // Two components claiming a whole file cannot silently overwrite each other.
    def collision = new Kitchen(conflictRoot)
    ['compose-dev', 'local-aws-stack'].each { name ->
        def station = collision.station(name)
        station.output('docker-compose.yaml', [owns: true]) { text { 'services: {}\n' } }
        station.mount()
    }
    try { collision.plan(); assert false: 'Whole-file ownership must be exclusive' }
    catch (IllegalArgumentException expected) {
        assert expected.message.contains('docker-compose.yaml')
        assert expected.message.contains('Multiple writers')
    }
    assert !new File(conflictRoot, 'docker-compose.yaml').exists()
}
