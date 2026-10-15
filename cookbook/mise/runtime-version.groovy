/**
 * Pins a runtime version in mise and removes that runtime's separate version file.
 *
 * ## Before applying
 *
 * Choose the runtime and its version explicitly. This recipe does not read the version from `.nvmrc` or
 * `.python-version`; the selected file is deleted. mise must be installed to use the pin.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:mise:0.1.0:runtime-version --runtime=node --version=22.12.0
 * ./cook dev.thecook:mise:0.1.0:runtime-version --runtime=python --version=3.13.2
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `mise.toml` | `tools.<runtime>`, owned whole | Always |
 * | `.nvmrc` | Deleted | `--runtime=node` |
 * | `.python-version` | Deleted | `--runtime=python` |
 *
 * ## After applying
 *
 * Run `mise install` to install the pinned runtime. mise holds the selected runtime's version for the project.
 *
 * ## With other recipes
 *
 * Other recipes can declare tasks and different tools in the same `mise.toml`. A recipe that also pins the
 * selected runtime shares its `tools.<runtime>` target; apply this recipe last for `--version` to own that pin.
 */
import dev.thecook.mise.Mise
import dev.thecook.node.Node
import dev.thecook.python.Python

@param(value = ['runtime'], choices = ['node', 'python'], doc = 'The runtime whose version moves to mise') String runtime
@param(value = ['version'], doc = 'The version mise pins') String version

@ingredient(Mise) def mise
@ingredient(Node) def node
@ingredient(Python) def python

mise.tool(runtime, version)
[node: node, python: python][runtime].dropVersionFile()
