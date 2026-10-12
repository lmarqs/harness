return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def version = operations.required(options, 'version')
    def requirements = operations.required(options, 'requirements')
    def station = kitchen.station(options.id ?: 'python-uv')
    def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: options.adopt])
    def python = book.ingredient('python', kitchen, [version: version, mise: mise, venv: options.venv ?: 'mise', adopt: options.adopt])
    def uv = book.ingredient('uv-pip', kitchen, [python: python, mise: mise, requirements: requirements, adopt: options.adopt])
    [mise, python, uv].each { station.includes(it) }
    if (options.project instanceof Map) station.output(options.projectPath ?: 'pyproject.toml', [format: 'toml']) {
        options.project.each { name, value -> key(['project', name.toString()], [adopt: options.adopt ?: false]) { value } }
    }
    station
}
