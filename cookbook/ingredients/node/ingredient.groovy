return { kitchen, book, Map options ->
    def node = kitchen.ingredient(options.id ?: 'node')
    node.version = (options.version ?: '22').toString()
    node.adopt = options.adopt?.toString()?.toBoolean() ?: false
    node.input('packageFields')
    node.packageFields += options.packageFields ?: []
    if (options.mise) node.requires(options.mise)
    node.validate {
        if (!(node.version ==~ /[0-9]+(\.[0-9]+){0,2}/)) throw new IllegalArgumentException('Node version must be numeric')
    }
    node.export('tools') { [[node: node.version]] }
    node.export('files.ephemeral') { [[path: 'node_modules', scope: 'anywhere', kind: 'directory']] }
    def merge = book.script('ingredients/node/inputs.groovy')
    node.output(options.path ?: 'package.json', [format: 'json']) {
        view {
            key(['engines', 'node'], [adopt: node.adopt.toString().toBoolean()]) { '^' + node.version.tokenize('.')[0] + '.0.0' }
            key(['devDependencies', '@types/node'], [adopt: node.adopt.toString().toBoolean(), whenPresent: true]) {
                '^' + node.version.tokenize('.')[0] + '.0.0'
            }
            merge(node.packageFields.value, 'Node package field').each { name, value ->
                key([name], [adopt: node.adopt.toString().toBoolean()]) { value }
            }
        }
    }
    node
}
