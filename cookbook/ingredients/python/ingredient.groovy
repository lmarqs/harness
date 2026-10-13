return { kitchen, book, Map options ->
  if (!options.version) throw new IllegalArgumentException('python requires a version')
  def tool = kitchen.ingredient(options.id ?: 'python')
  tool.version = options.version.toString()
  tool.export('tools') { [[python: tool.version]] }
  if (options.mise) {
    tool.requires(options.mise)
    options.mise.tools.from(tool.tools)
  }
  tool.venv = options.venv ?: 'mise'
  if (!(tool.venv in ['mise', 'none'])) throw new IllegalArgumentException('Python venv must be mise or none')
  tool.export('files.ephemeral') {
    [
      [path: '__pycache__', scope: 'anywhere', kind: 'directory'],
      [path: '*.py[cod]', scope: 'anywhere', kind: 'file']
    ]
  }
  tool.export('files.local') {
    tool.venv == 'mise' ? [[path: '.venv', scope: 'root', kind: 'directory']] : []
  }
  if (tool.venv == 'mise') {
    if (!options.mise) throw new IllegalArgumentException('Python venv=mise requires a mise component')
    tool.export('env') { [[path: ['_', 'python', 'venv'], value: [path: '.venv', create: true]]] }
    options.mise.env.from(tool.env)
  }
  tool
}
