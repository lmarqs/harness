@Ingredient
class Node {
  String version = '22'
  String manager = 'npm'       // npm | pnpm | yarn
  String managerVersion
  Map<String, Map<String, String>> dependencies = [:]   // kind -> package -> version
  boolean keepVersionFile = true

  static final Map DEFAULT_MANAGER_VERSION = [npm: '10.9.8', pnpm: '10.4.1', yarn: '4.17.0']

  void manager(String name, String version = null) {
    if (!(name in ['npm', 'pnpm', 'yarn'])) throw new IllegalArgumentException("Unknown package manager: ${name}")
    manager = name
    managerVersion = version ?: DEFAULT_MANAGER_VERSION[name]
  }

  void dependency(String packageName, String version, String kind = 'dependencies') {
    if (!(packageName ==~ /(@[a-zA-Z0-9_.-]+\/)?[a-zA-Z0-9_.-]+/)) throw new IllegalArgumentException("Invalid package name: ${packageName}")
    if (!(kind in ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies'])) throw new IllegalArgumentException("Invalid dependency kind: ${kind}")
    dependencies.get(kind, [:])[packageName] = version
  }

  void dropVersionFile() { keepVersionFile = false }

  Map<String, String> tools() { [node: version] + (managerVersion ? [(manager == 'npm' ? 'npm:npm' : manager): managerVersion] : [:]) }

  List<String> ephemeralFiles() {
    ['node_modules/'] + (manager == 'yarn' ? ['yarn-error.log*', '/.yarn/install-state.gz', '/.yarn/unplugged/'] : ['npm-debug.log*'])
  }

  String setupScript() { resource("${manager}/setup.sh") }

  String lockScript() { resource("${manager}/lock.sh") }

  String installCommand() { "cd ${Mise.quote(directory)} && ${manager} install" }

  @Key(path = 'package.json', segments = ['engines', 'node'])
  String engine() { "^${version.tokenize('.')[0]}.0.0" }

  @Key(path = 'package.json', segments = ['packageManager'])
  String packageManager() { managerVersion ? "${manager}@${managerVersion}" : null }

  @Keys(path = 'package.json', under = ['dependencies'])
  Map runtimeDependencies() { dependencies.dependencies ?: null }

  @Keys(path = 'package.json', under = ['devDependencies'])
  Map developmentDependencies() { dependencies.devDependencies ?: null }

  @Delete(path = '.nvmrc')
  boolean nvmrc() { !keepVersionFile }
}
