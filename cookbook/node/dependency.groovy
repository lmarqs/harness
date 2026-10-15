/**
 * Records one dependency in a package manifest and declares a mise task that installs it.
 *
 * ## Before applying
 *
 * Choose the package directory and its package manager. mise and the selected package manager must be installed
 * to run the install task. The version range is recorded as supplied.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:node:0.1.0:dependency --package=zod --version=^3.23.0 --dir=packages/api
 * ./cook dev.thecook:node:0.1.0:dependency --package=typescript --version=^5.7.0 --kind=devDependencies --manager=yarn
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `<dir>/package.json` | `<kind>.<package>`, owned whole | Always |
 * | `mise.toml` | `tasks."dependency:node"`, owned whole, running the selected manager in `<dir>` | Always |
 *
 * ## After applying
 *
 * Run `mise run dependency:node` to install dependencies in the selected directory. Applying the recipe records
 * the manifest entry and task; the task runs the package manager and updates its lock file.
 *
 * ## With other recipes
 *
 * `dev.thecook:node:0.1.0:project` supplies tool pins and setup tasks for a root project. Use the same manager
 * and `--dir=.` to target that project. Each application of this dependency recipe declares the same
 * `tasks."dependency:node"` key; the last recipe in an invocation owns the task and selects its directory.
 */
import dev.thecook.node.Node
import dev.thecook.mise.Mise

@param(value = ['package'], doc = 'The package name') String packageName
@param(value = ['version'], doc = 'The version range to record') String version
@param(value = ['kind'], choices = ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies'], default = 'dependencies', doc = 'The package.json dependency group that owns the entry') String kind
@param(value = ['manager'], choices = ['npm', 'pnpm', 'yarn'], default = 'npm', doc = 'The package manager the install task runs') String manager
@param(value = ['dir'], default = '.', doc = 'The package directory') String dir

@ingredient(Node) def node
@ingredient(Mise) def mise

node.directory = dir
node.manager(manager)
node.dependency(packageName, version, kind)
mise.task('dependency:node', node.installCommand())
