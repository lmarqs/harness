return { kitchen, book, Map options ->
    def npm = kitchen.ingredient(options.id ?: 'npm')
    npm.version = (options.version ?: '10.9.8').toString()
    npm.slot = options.slot ?: 'node-package-manager'
    if (options.node) npm.requires(options.node)
    npm.validate {
        if (!(npm.version ==~ /[0-9]+\.[0-9]+\.[0-9]+/)) throw new IllegalArgumentException('npm version must be exact')
    }
    npm.export('tools') { [['npm:npm': npm.version]] }
    npm.export('packageFields') { [[packageManager: 'npm@' + npm.version]] }
    npm.export('files.ephemeral') { [[path: 'npm-debug.log*', scope: 'anywhere', kind: 'file']] }
    npm.export('tasks') {
        [[name: 'node:setup', script: book.text('ingredients/npm/setup.sh')],
         [name: 'node:lock', script: book.text('ingredients/npm/lock.sh')]]
    }
    npm
}
