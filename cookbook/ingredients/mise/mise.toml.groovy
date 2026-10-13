return { mise, book, Map options ->
  def merge = book.script('ingredients/mise/inputs.groovy')
  mise.output(options.path ?: 'mise.toml', [format: 'toml']) {
    view {
      ['tools', 'settings'].each { category ->
        merge(mise.getProperty(category).value, "mise ${category}").each { name, value ->
          key([category, name], [adopt: mise.adopt.toString().toBoolean()]) { value }
        }
      }
      mise.tasks.value.findAll { !it.script }.each { task ->
        key(['tasks', task.name.toString()], [adopt: mise.adopt.toString().toBoolean()]) {
          task.findAll {
            it.key != 'name'
          }
        }
      }
      def environments = [:]
      mise.env.value.each { entry -> environments[entry.path.toList()] = entry.value }
      environments.each { path, value ->
        key(['env'] + path, [adopt: mise.adopt.toString().toBoolean()]) { value }
      }
    }
  }
  mise.view {
    def scripts = [:]
    mise.tasks.value.findAll { it.script }.each { task ->
      def name = task.name?.toString()
      if (!name || !(name ==~ /[a-zA-Z0-9_-]+(:[a-zA-Z0-9_-]+)*/))
        throw new IllegalArgumentException("Invalid mise task name: ${name}")
      if (scripts.containsKey(name) && scripts[name] != task.script)
        throw new IllegalArgumentException("Conflicting mise task: ${name}")
      scripts[name] = task.script.toString()
    }
    scripts.each { name, script ->
      output('.mise/tasks/' + name.replace(':', '/'), [owns: true, mode: '0755', adopt: mise.adopt.toString().toBoolean()]) {
        text { script }
      }
    }
  }
  mise
}
