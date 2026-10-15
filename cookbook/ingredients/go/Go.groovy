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

  Map tidyCommand() { [run: [['go', 'mod', 'tidy'], ['go', 'test', './...']], dir: directory] }

  /** One line per requirement: kept as written when already required at this version, appended when absent. */
  @Lines(path = 'go.mod')
  Map<String, String> requireLines() {
    if (!requires) return null
    def text = current('go.mod')
    if (text == null) throw new IllegalArgumentException("${directory}/go.mod must exist")
    requires.collectEntries { module, version ->
      def line = ~/^\s*(?:require\s+)?${Pattern.quote(module)}\s+(\S+)/
      def existing = text.readLines().find { it =~ line }
      if (existing && (existing =~ line)[0][1] != version)
        throw new IllegalArgumentException("${module} is required at another version; change the existing declaration")
      [line.pattern(), existing ?: "require ${module} ${version}"]
    }
  }
}
