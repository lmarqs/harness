package dev.thecook.python

import java.util.regex.Pattern

/**
 * Models a Python project: its requirements, virtual environment and installer.
 *
 * ## Writes for
 *
 * Python project metadata in `pyproject.toml`, with requirement strings in `project.dependencies` and
 * `project.optional-dependencies.<group>`, or pip requirements-file syntax in `requirements.txt`.
 * `.python-version` is the interpreter version file removed when mise owns the version.
 *
 * ## Instances
 *
 * One instance covers one project directory. A subproject uses a second instance `at` its directory, with
 * its own manifest and virtual environment.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.python.Python
 * import dev.thecook.mise.Mise
 * import dev.thecook.git.Git
 *
 * @ingredient(Python) def python
 * @ingredient(Mise) def mise
 * @ingredient(Git) def git
 *
 * python.version = '3.13.2'
 * python.dependency('httpx>=0.27', 'dev')
 * mise.tools(python.tools())
 * python.env().each { name, value -> mise.env(name, value) }
 * mise.task('dependency:python', python.installCommand())
 * git.ignore(python.ephemeralFiles())
 * git.ignore(python.localFiles())
 * ```
 *
 * ## With other models
 *
 * `tools()` and `env()` supply mise tool pins and virtual-environment configuration. `installCommand()`
 * supplies a task runner command; `ephemeralFiles()` and `localFiles()` supply Git ignore patterns.
 * The recipe passes these facts to the consuming models.
 */
@Ingredient
class Python {
  /** The interpreter version exposed by `tools()` for mise; unset, no Python tool pin is supplied. */
  String version

  /** The installer used by `installCommand()`: `uv` by default, or `pip`. */
  String installer = 'uv'      // uv | pip

  /** The virtual-environment path relative to the project directory, `.venv` by default. */
  String venv = '.venv'

  private Map<String, List<String>> added = [:]   // dependency group ('' is the main list) -> requirements
  private List<String> requirements = []          // for requirements.txt projects
  private boolean keepVersionFile = true

  /**
   * Adds one requirement to the main dependency list, or to the named optional group. Newlines and strings
   * without a leading package name are rejected. Reads `pyproject.toml` through `current` and rejects a
   * different specification for the same normalized package name already present in that group.
   */
  void dependency(String requirement, String group = '') {
    if (requirement.contains('\n')) throw new IllegalArgumentException('One requirement per call')
    def name = packageName(requirement)
    if (declared(group).any { it != requirement && packageName(it) == name })
      throw new IllegalArgumentException("${name} already present with another specification")
    added.get(group, []) << requirement
  }

  /** Adds a line as supplied to the managed `dependencies` section of `requirements.txt`. */
  void requirement(String line) { requirements << line }

  /** Removes `.python-version`, for a project whose interpreter version mise owns. */
  void dropVersionFile() { keepVersionFile = false }

  /** The `python` tool pin for mise when an interpreter version is set; otherwise an empty map. */
  Map<String, String> tools() { version ? [python: version] : [:] }

  /** mise environment configuration selecting `venv` and creating that virtual environment when absent. */
  Map env() { [_: [python: [venv: [path: venv, create: true]]]] }

  /** Git ignore patterns for Python bytecode and cache directories. */
  List<String> ephemeralFiles() { ['__pycache__/', '*.py[cod]'] }

  /** A Git ignore pattern for the virtual environment, rooted in this instance's directory. */
  List<String> localFiles() { ["/${venv}/"] }

  /**
   * The install command, run in this instance's directory, using the selected installer and virtual environment.
   * Call after adding requirements: it installs `requirements.txt` when any requirement lines are supplied,
   * otherwise the editable project with every optional group touched by `dependency`.
   */
  Map installCommand() {
    def groups = added.keySet().findAll { it }
    def target = requirements ? ['-r', 'requirements.txt'] : ['-e', groups ? ".[${groups.join(',')}]" : '.']
    def install = installer == 'uv' ? ['uv', 'pip', 'install', '--python', venv] : ["${venv}/bin/python", '-m', 'pip', 'install']
    [run: [install + target], dir: directory]
  }

  /**
   * One list entry per added requirement, matched by package name, in `project.dependencies` or its optional
   * group in `pyproject.toml`, when `dependency` is called.
   */
  @Keys(path = 'pyproject.toml', under = ['project'])
  Map dependencyEntries() {
    added.collectEntries { group, list ->
      def under = group ? ['optional-dependencies', group] : ['dependencies']
      list.collectEntries { [(under + [[matches: namePattern(it)]]): it] }
    } ?: null
  }

  /** The `dependencies` section of `requirements.txt`, when requirement lines are supplied. */
  @Section(path = 'requirements.txt', id = 'dependencies')
  List<String> requirementsSection() { requirements ?: null }

  /** Deletes `.python-version` when `dropVersionFile()` is called. */
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
