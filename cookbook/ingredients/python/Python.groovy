@Ingredient
class Python {
  String version
  String installer = 'uv'      // uv | pip
  String venv = '.venv'
  Map<String, List<String>> added = [:]   // dependency group ('' is the main list) -> requirements
  List<String> requirements = []          // for requirements.txt projects
  boolean keepVersionFile = true

  void dependency(String requirement, String group = '') {
    if (requirement.contains('\n')) throw new IllegalArgumentException('One requirement per call')
    def name = packageName(requirement)
    if (declared(group).any { it != requirement && packageName(it) == name })
      throw new IllegalArgumentException("${name} already present with another specification")
    added.get(group, []) << requirement
  }

  void requirement(String line) { requirements << line }

  void dropVersionFile() { keepVersionFile = false }

  Map<String, String> tools() { version ? [python: version] : [:] }

  Map env() { [_: [python: [venv: [path: venv, create: true]]]] }

  List<String> ephemeralFiles() { ['__pycache__/', '*.py[cod]'] }

  List<String> localFiles() { ["/${venv}/"] }

  /** Installs what this instance declared: the requirements file, or the project with every group it touched. */
  Map installCommand() {
    def groups = added.keySet().findAll { it }
    def target = requirements ? ['-r', 'requirements.txt'] : ['-e', groups ? ".[${groups.join(',')}]" : '.']
    def install = installer == 'uv' ? ['uv', 'pip', 'install', '--python', venv] : ["${venv}/bin/python", '-m', 'pip', 'install']
    [run: [install + target], dir: directory]
  }

  /** One list entry per added requirement, matched by package name, in `project.dependencies` or its group. */
  @Keys(path = 'pyproject.toml', under = ['project'])
  Map dependencyEntries() {
    added.collectEntries { group, list ->
      def under = group ? ['optional-dependencies', group] : ['dependencies']
      list.collectEntries { [(under + [[matches: namePattern(it)]]): it] }
    } ?: null
  }

  @Section(path = 'requirements.txt', id = 'dependencies')
  List<String> requirementsSection() { requirements ?: null }

  @Delete(path = '.python-version')
  boolean pythonVersionFile() { !keepVersionFile }

  private List<String> declared(String group) {
    def text = current('pyproject.toml')
    if (text == null) return []
    def project = new TomlSlurper().parseText(text).project
    (group ? project?.'optional-dependencies'?.get(group) : project?.dependencies) ?: []
  }

  private static String packageName(String requirement) {
    def match = requirement =~ /^([A-Za-z0-9_.-]+)/
    if (!match.find()) throw new IllegalArgumentException("Requirement must start with a package name: ${requirement}")
    match.group(1).toLowerCase().replaceAll(/[-_.]+/, '-')
  }

  /** Matches a requirement string for the same package, whatever its separators and case. */
  private static String namePattern(String requirement) {
    "(?i)^${packageName(requirement).split('-').collect { Pattern.quote(it) }.join('[-_.]+')}(?![A-Za-z0-9_.-])"
  }
}
