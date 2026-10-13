def recipe = new File(scriptDirectory, 'cookbook/recipe.groovy')
new GroovyShell(getClass().classLoader, new Binding([
    preferences: [policy: '.agents/policy.yaml'] + preferences,
    scriptDirectory: recipe.absoluteFile.parentFile
])).evaluate(recipe)
