package dev.thecook.node

/**
 * A Node project: `package.json`, its package manager and the files the toolchain leaves behind.
 *
 * ## Writes for
 *
 * | Program | Format or version |
 * | --- | --- |
 * | Node | `engines.node` as a caret range on the major you set |
 * | npm, pnpm, Yarn | `packageManager` as Corepack reads it; defaults npm 10.9.8, pnpm 10.4.1, Yarn 4.17.0 |
 * | mise | tool names `node`, `npm:npm`, `pnpm` and `yarn` |
 *
 * ## Instances
 *
 * One instance per project directory. A workspace package is a second instance `at` its directory, and each
 * owns its own `package.json`.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.node.Node
 * import dev.thecook.mise.Mise
 * import dev.thecook.git.Git
 *
 * @ingredient(Node) def node
 * @ingredient(Mise) def mise
 * @ingredient(Git) def git
 *
 * node.version = '22'
 * node.manager('yarn')
 * node.pinManager()
 * mise.tools(node.tools())
 * mise.script('node:setup', node.setupScript())
 * git.ignore(node.ephemeralFiles())
 * ```
 *
 * ## With other models
 *
 * `tools()` is for mise, `ephemeralFiles()` for Git, and `setupScript()`, `lockScript()` and `installCommand()`
 * for a task runner. This model writes none of those; the recipe hands them over.
 */
@Ingredient
class Node {
  /** The Node version, a major such as `22` or an exact one. Unset, `engines.node` is left alone. */
  String version

  private String manager = 'npm'
  private String managerVersion
  private Map<String, Map<String, String>> dependencies = [:]   // kind -> package -> version
  private boolean keepVersionFile = true

  private static final Map DEFAULT_MANAGER_VERSION = [npm: '10.9.8', pnpm: '10.4.1', yarn: '4.17.0']
  private static final List<String> KINDS = ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies']

  /** Selects `npm`, `pnpm` or `yarn` as the package manager. Any other name is rejected. */
  void manager(String name) {
    if (!(name in DEFAULT_MANAGER_VERSION.keySet())) throw new IllegalArgumentException("Unknown package manager: ${name}")
    manager = name
  }

  /** Pins the selected manager, in `packageManager` and in `tools()`, at `version` or the manager's default. */
  void pinManager(String version = null) { managerVersion = version ?: DEFAULT_MANAGER_VERSION[manager] }

  /**
   * Records one dependency of `kind`, `dependencies` by default, at the version range as written.
   * A malformed package name or a kind outside `package.json`'s four is rejected.
   */
  void dependency(String packageName, String version, String kind = 'dependencies') {
    if (!(packageName ==~ /(@[a-zA-Z0-9_.-]+\/)?[a-zA-Z0-9_.-]+/)) throw new IllegalArgumentException("Invalid package name: ${packageName}")
    if (!(kind in KINDS)) throw new IllegalArgumentException("Invalid dependency kind: ${kind}")
    dependencies.get(kind, [:])[packageName] = version
  }

  /** Removes `.nvmrc`, for a project whose Node version mise owns. */
  void dropVersionFile() { keepVersionFile = false }

  /** Tool versions for mise: `node` when a version was set, and the pinned manager as `npm:npm`, `pnpm` or `yarn`. */
  Map<String, String> tools() {
    (version ? [node: version] : [:]) + (managerVersion ? [(manager == 'npm' ? 'npm:npm' : manager): managerVersion] : [:])
  }

  /** Ignore patterns for what the selected manager leaves behind, for Git. */
  List<String> ephemeralFiles() {
    ['node_modules/'] + (manager == 'yarn' ? ['yarn-error.log*', '/.yarn/install-state.gz', '/.yarn/unplugged/'] : ['npm-debug.log*'])
  }

  /** The selected manager's setup script, for a task runner. Ships inside this artifact. */
  String setupScript() { resource("${manager}/setup.sh") }

  /** The selected manager's lock refresh script, for a task runner. Ships inside this artifact. */
  String lockScript() { resource("${manager}/lock.sh") }

  /** The install command, run in this instance's directory, for a task runner. */
  Map installCommand() { [run: [[manager, 'install']], dir: directory] }

  /** `engines.node`, a caret range on the major, once a version was set. */
  @Key(path = 'package.json', segments = ['engines', 'node'])
  String engine() { version ? "^${version.tokenize('.')[0]}.0.0" : null }

  /** `packageManager`, as Corepack reads it, once the manager was pinned. */
  @Key(path = 'package.json', segments = ['packageManager'])
  String packageManager() { managerVersion ? "${manager}@${managerVersion}" : null }

  /** `<kind>.<package>` per recorded dependency, each owned on its own. */
  @Keys(path = 'package.json')
  Map dependencyKeys() {
    dependencies.collectEntries { kind, packages -> packages.collectEntries { name, range -> [[kind, name], range] } } ?: null
  }

  /** `.nvmrc`, after `dropVersionFile()`. */
  @Delete(path = '.nvmrc')
  boolean nvmrc() { !keepVersionFile }
}
