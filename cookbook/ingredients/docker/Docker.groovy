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

  String buildCommand(String stage) { "docker build -f ${Mise.quote(dockerfile)} --target ${Mise.quote(stage)} ." }

  @File(path = '${dockerfile}')
  String dockerfileText() {
    if (!stages) return null
    def lines = new File(directory, dockerfile).readLines()
    stages.each { stage, change ->
      def matches = lines.findIndexValues { it ==~ /(?i)^\s*FROM\s+(--platform=\S+\s+)?(\S+)\s+AS\s+${stage}\s*$/ }
      if (matches.size() != 1) throw new IllegalArgumentException("Expected exactly one stage ${stage} in ${dockerfile}")
      def line = lines[matches[0] as int]
      if (!(line.contains(" ${change.expected} ") || line.contains(" ${change.image} ")))
        throw new IllegalArgumentException("Stage ${stage} does not use ${change.expected}")
      lines[matches[0] as int] = line.replace(" ${change.expected} ", " ${change.image} ")
    }
    lines.join('\n') + '\n'
  }

  @File(path = '.dockerignore')
  String ignoreFile() { ignore ? ignore.unique().join('\n') + '\n' : null }
}
