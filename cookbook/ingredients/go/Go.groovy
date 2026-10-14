@Ingredient
class Go {
  String version
  Map<String, String> requires = [:]

  void require(String module, String version) {
    if (!(module ==~ /[a-zA-Z0-9_.\/-]+/) || !(version ==~ /v[0-9][a-zA-Z0-9_.+-]*/))
      throw new IllegalArgumentException("Module and version must be literal: ${module} ${version}")
    requires[module] = version
  }

  Map<String, String> tools() { version ? [go: version] : [:] }

  String tidyCommand() { "cd ${Mise.quote(directory)} && go mod tidy && go test ./..." }

  @File(path = 'go.mod')
  String goMod() {
    if (!requires) return null
    def file = new File(directory, 'go.mod')
    if (!file.isFile()) throw new IllegalArgumentException("${file} must exist")
    def text = file.text
    requires.each { module, version ->
      def prefixes = ["${module} ", "require ${module} "]
      def declared = text.readLines().find { line -> prefixes.any { line.trim().startsWith(it) } }
      if (declared && !(declared.trim() in ["${module} ${version}", "require ${module} ${version}"]))
        throw new IllegalArgumentException("${module} is required at another version; change the existing declaration")
      if (!declared) text += (text.endsWith('\n') ? '' : '\n') + "require ${module} ${version}\n"
    }
    text
  }
}
