// Small shared composition operations; all changes remain kitchen outputs.
return [
    required: { Map options, String name ->
        if (!options.containsKey(name) || options[name] == null || options[name] == '')
            throw new IllegalArgumentException("Required option: ${name}")
        options[name]
    },
    task: { kitchen, book, component, Map options, String name, String run ->
        def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: options.adopt])
        mise { tasks += [[name: name, run: run]] }
        component.includes(mise)
        mise
    },
    quote: { Object value -> "'" + value.toString().replace("'", "'\"'\"'") + "'" },
    whole: { component, String path, String content, boolean adopt ->
        component.output(path, [owns: true, adopt: adopt]) { text { content } }
    },
    replacements: { kitchen, component, Map options ->
        if (options.adopt != true) throw new IllegalArgumentException('Migration requires adopt: true')
        if (!(options.files instanceof List) || !options.files) throw new IllegalArgumentException('Migration requires files: [[path, expected, content]]')
        options.files.each { change ->
            if (!(change instanceof Map) || !change.path || !(change.expected instanceof String) || !(change.content instanceof String))
                throw new IllegalArgumentException('Each migration file requires path, expected and content strings')
            def file = kitchen.repository.target(change.path.toString())
            if (!file.isFile() || !(file.getText('UTF-8') in [change.expected, change.content]))
                throw new IllegalArgumentException("Migration source differs: ${change.path}")
            component.output(change.path.toString(), [owns: true, adopt: true]) { text { change.content } }
        }
    }
]
