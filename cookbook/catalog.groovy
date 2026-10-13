// Run from the repository root with Groovy and the Cook runtime on the classpath.
// --verify executes the examples against temporary repositories.
def root = new File(System.getProperty('thecook.cookbook', 'cookbook')).canonicalFile
assert root.isDirectory(): "Cookbook directory not found: ${root}"
def definitions = []
['ingredients': 'ingredient.groovy', 'stations': 'station.groovy', 'recipes': 'recipe.groovy', 'targets': 'target.groovy'].each { category, filename ->
  new File(root, category).eachFileRecurse { file ->
    if (file.isFile() && file.name == filename) {
      def relative = root.toPath().relativize(file.toPath()).toString()
      if (!relative.tokenize('/').any { it in ['build', '.gradle', '.git'] }) definitions << relative
    }
  }
}
def verifications = []
new File(root, 'examples').eachFileRecurse { file ->
  if (file.isFile() && file.name in ['case.groovy', 'cases.groovy']) verifications << file
}
definitions.sort().each { println it }
verifications.sort { it.path }.each { println root.toPath().relativize(it.toPath()) }
def repositoryScript = new File(root, 'examples/typed-runtime/cook.groovy')
assert repositoryScript.isFile()
println root.toPath().relativize(repositoryScript.toPath())
if (binding.hasVariable('args') && args.contains('--verify')) {
  verifications.each { file -> assert new GroovyShell(getClass().classLoader).evaluate(file) == true }
}
definitions + verifications.collect { root.toPath().relativize(it.toPath()).toString() } + ['examples/typed-runtime/cook.groovy']
