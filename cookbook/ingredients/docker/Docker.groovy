@Ingredient
class Docker {
  String dockerfile = 'Dockerfile'
  List<String> ignore = null
  Map<String, Map> stages = [:]   // stage -> [image, expected]

  void ignore(String... patterns) { ignore = (ignore ?: []) + patterns }

  void baseImage(String stage, String image, String expected) {
    if (!(image ==~ /[a-zA-Z0-9_.:@\/-]+/)) throw new IllegalArgumentException("Image must be a literal reference: ${image}")
    stages[stage] = [image: image, expected: expected]
  }

  Map buildCommand(String stage) { [run: [['docker', 'build', '-f', dockerfile, '--target', stage, '.']], dir: directory] }

  /** The `FROM` line of each changed stage. The stage must exist exactly once and use the expected or the new image. */
  @Lines(path = '${dockerfile}')
  Map<String, String> stageLines() {
    if (!stages) return null
    def text = current(dockerfile)
    if (text == null) throw new IllegalArgumentException("${dockerfile} must exist")
    stages.collectEntries { stage, change ->
      def from = ~/(?i)^\s*FROM\s+(--platform=\S+\s+)?\S+\s+AS\s+${Pattern.quote(stage)}\s*$/
      def matches = text.readLines().findAll { it =~ from }
      if (matches.size() != 1) throw new IllegalArgumentException("Expected exactly one stage ${stage} in ${dockerfile}")
      def line = matches[0]
      if (!(line.contains(" ${change.expected} ") || line.contains(" ${change.image} ")))
        throw new IllegalArgumentException("Stage ${stage} does not use ${change.expected}")
      [from.pattern(), line.replace(" ${change.expected} ", " ${change.image} ")]
    }
  }

  @File(path = '.dockerignore')
  String ignoreFile() { ignore ? ignore.unique().join('\n') + '\n' : null }
}
