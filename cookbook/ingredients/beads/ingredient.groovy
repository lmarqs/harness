return { kitchen, book, Map options ->
    def beads = kitchen.ingredient(options.id ?: 'beads')
    beads.issuePrefix = options.issuePrefix
    beads.adopt = options.adopt?.toString()?.toBoolean() ?: false
    beads.output('.beads/config.yaml', [format: 'yaml']) {
        view {
            if (beads.issuePrefix) key(['issue-prefix'], [adopt: beads.adopt.toString().toBoolean()]) { beads.issuePrefix }
            key(['json'], [adopt: beads.adopt.toString().toBoolean()]) { true }
        }
    }
    beads.export('files.local') {
        [[path: '.beads/dolt', scope: 'root', kind: 'directory'],
         [path: '.beads/*.db*', scope: 'root', kind: 'file']]
    }
    beads.export('files.ephemeral') {
        [[path: '.beads/*.lock', scope: 'root', kind: 'file'],
         [path: '.beads/daemon.log', scope: 'root', kind: 'file']]
    }
    beads.export('tasks') {
        [[name: 'beads:ready', script: book.text('ingredients/beads/ready.sh')]]
    }
    beads
}
