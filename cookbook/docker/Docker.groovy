package dev.thecook.docker

/**
 * Models named build stages in a Dockerfile and the build context's ignore file.
 *
 * ## Writes for
 *
 * Dockerfile `FROM ... AS <stage>` lines, preserving an optional `--platform` argument, and Docker's
 * `.dockerignore` pattern syntax. Each requested stage line is owned independently; the ignore file is owned whole.
 *
 * ## Instances
 *
 * One instance covers one build context directory and a selected Dockerfile, `Dockerfile` by default. Use `at`
 * for another context directory. The build command runs in that directory with `.` as its build context.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.docker.Docker
 * import dev.thecook.mise.Mise
 *
 * @ingredient(Docker) def docker
 * @ingredient(Mise) def mise
 *
 * docker.baseImage('build', 'eclipse-temurin:21', 'eclipse-temurin:17')
 * docker.ignore('.git', 'build')
 * mise.task('docker:verify', docker.buildCommand('build'))
 * ```
 *
 * ## With other models
 *
 * `buildCommand(stage)` supplies a command for Mise to build the named stage. Ignore patterns exposed by
 * other models can be passed to `ignore`; the recipe wires these facts into the build context.
 */
@Ingredient
class Docker {
  /** The Dockerfile path inside the instance's build context directory; `Dockerfile` by default. */
  String dockerfile = 'Dockerfile'
  private List<String> ignore = null
  private Map<String, Map> stages = [:]   // stage -> [image, expected]

  /** Adds Docker ignore patterns as supplied; repeated patterns appear only once in the whole ignore file. */
  void ignore(String... patterns) { ignore = (ignore ?: []) + patterns }

  /**
   * Requests a base image for a named stage, replacing an earlier request for that stage. The new image must
   * be a literal reference using letters, digits, underscores, dots, colons, at signs, slashes or hyphens.
   * Declaration rejects an absent Dockerfile, a stage that does not occur exactly once, or a stage whose image
   * is neither `expected` nor the requested image. A stage already using the requested image stays in place.
   */
  void baseImage(String stage, String image, String expected) {
    if (!(image ==~ /[a-zA-Z0-9_.:@\/-]+/)) throw new IllegalArgumentException("Image must be a literal reference: ${image}")
    stages[stage] = [image: image, expected: expected]
  }

  /** Supplies a command to build the named stage from the selected Dockerfile in the instance's context directory. */
  Map buildCommand(String stage) { [run: [['docker', 'build', '-f', dockerfile, '--target', stage, '.']], dir: directory] }

  /**
   * Each requested stage's `FROM` line in the selected Dockerfile, owned independently, when base images are
   * requested. The file must exist; each stage must occur exactly once and use the expected or the requested image.
   */
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

  /** The whole `.dockerignore` file, with unique patterns and a final newline, when ignore patterns are supplied. */
  @File(path = '.dockerignore')
  String ignoreFile() { ignore ? ignore.unique().join('\n') + '\n' : null }
}
