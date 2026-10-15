/**
 * Sets up a Node project with npm or Yarn: tool versions in `mise.toml`, the engine and package manager in
 * `package.json`, setup and lock tasks, and Git ignores for what those tools leave behind.
 *
 * ## Before applying
 *
 * mise must be installed to use the tool pins and tasks. The setup task installs dependencies from an existing
 * lock file; use the lock task to create or refresh it.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:node:0.1.0:project --manager=yarn
 * ./cook dev.thecook:node:0.1.0:project --node=22 --manager=npm --manager-version=10.9.8
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `package.json` | `engines.node` and `packageManager`, each owned whole | Always |
 * | `mise.toml` | `tools.node`, owned whole | Always |
 * | `mise.toml` | `tools."npm:npm"`, owned whole | `--manager=npm` |
 * | `mise.toml` | `tools.yarn`, owned whole | `--manager=yarn` |
 * | `.mise/tasks/node/setup`, `.mise/tasks/node/lock` | The whole executable scripts | Always |
 * | `.gitignore` | The section `ignore`: the manager's ephemeral files | Always |
 *
 * ## After applying
 *
 * Run `mise install` to install the pinned tools. Run `mise run node:lock` when the lock file needs creating or
 * refreshing, and `mise run node:setup` to install dependencies from it.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:node:0.1.0:dependency` with the same manager and `--dir=.` to add a dependency to this
 * project. Its dependency keys compose with the engine and manager keys; refresh the lock file after changes.
 * Other recipes can add distinct tool pins and tasks in the same `mise.toml`.
 */
import dev.thecook.node.Node
import dev.thecook.mise.Mise
import dev.thecook.git.Git

@param(value = ['node'], default = '22', doc = 'The Node version') String nodeVersion
@param(value = ['manager'], choices = ['npm', 'yarn'], default = 'npm', doc = 'The package manager for the project tools and tasks') String manager
@param(value = ['manager-version'], default = null, doc = 'The package manager version to pin, or its default when unset') String managerVersion

@ingredient(Node) def node
@ingredient(Mise) def mise
@ingredient(Git) def git

node.version = nodeVersion
node.manager(manager)
node.pinManager(managerVersion)

mise.tools(node.tools())
mise.script('node:setup', node.setupScript())
mise.script('node:lock', node.lockScript())
git.ignore(node.ephemeralFiles())
