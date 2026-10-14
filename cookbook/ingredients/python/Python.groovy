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
    def existing = current(group)
    if (existing.any { it != requirement && packageName(it) == name })
      throw new IllegalArgumentException("${name} already present with another specification")
    added.get(group, []) << requirement
  }

  void requirement(String line) { requirements << line }

  void dropVersionFile() { keepVersionFile = false }

  Map<String, String> tools() { version ? [python: version] : [:] }

  Map env() { [_: [python: [venv: [path: venv, create: true]]]] }

  List<String> ephemeralFiles() { ['__pycache__/', '*.py[cod]'] }

  List<String> localFiles() { ["/${venv}/"] }

  String installCommand(String target) {
    installer == 'uv' ? "uv pip install --python ${Mise.quote(venv)} ${target}" : "${Mise.quote(venv)}/bin/python -m pip install ${target}"
  }

  @Key(path = 'pyproject.toml', segments = ['project', 'dependencies'])
  List<String> mainDependencies() { added[''] ? (current('') + added['']).unique() : null }

  @Keys(path = 'pyproject.toml', under = ['project', 'optional-dependencies'])
  Map optionalDependencies() {
    added.findAll { group, list -> group }.collectEntries { group, list -> [group, (current(group) + list).unique()] } ?: null
  }

  @Section(path = 'requirements.txt', id = 'dependencies')
  List<String> requirementsSection() { requirements ?: null }

  @Delete(path = '.python-version')
  boolean pythonVersionFile() { !keepVersionFile }

  private List<String> current(String group) {
    def file = new File(directory, 'pyproject.toml')
    if (!file.isFile()) return []
    def project = new TomlSlurper().parse(file).project
    (group ? project?.'optional-dependencies'?.get(group) : project?.dependencies) ?: []
  }

  private static String packageName(String requirement) {
    def match = requirement =~ /^([A-Za-z0-9_.-]+)/
    if (!match.find()) throw new IllegalArgumentException("Requirement must start with a package name: ${requirement}")
    match.group(1).toLowerCase().replaceAll(/[-_.]+/, '-')
  }
}
