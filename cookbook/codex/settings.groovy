/**
 * Writes chosen Codex configuration, hooks, rule files and instructions from files and arguments you supply.
 *
 * ## Before applying
 *
 * The optional configuration input is a TOML file and the optional hooks input is a JSON file in the destination.
 * Both are read as they exist before the invocation; each top-level key becomes one owned key. The optional
 * rules input is a directory of rule files, each with a simple name using letters, digits, underscores, dots
 * and hyphens, other than `.` or `..`.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:codex:0.1.0:settings --config=setup/codex.toml --rules=setup/codex-rules
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `.codex/config.toml` | Each top-level key of the config file, owned whole | `--config` supplies keys |
 * | `.codex/hooks.json` | Each top-level key of the hooks file, owned whole | `--hooks` supplies keys |
 * | `.codex/rules/<name>` | One whole file per file in the rules directory | `--rules` supplies files |
 * | `AGENTS.md` | The section `codex` | `--instructions` supplies text |
 *
 * ## After applying
 *
 * Codex reads the project configuration, hooks, rules and instructions. Apply again with updated inputs to
 * change them; review sandbox settings and command rules against Codex's permission model.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:agents:0.1.0:context` with `--harness=codex` to seed shared instructions in `AGENTS.md`;
 * its shared section and this recipe's `codex` section are separate targets. Apply
 * `dev.thecook:agents:0.1.0:permissions` after this recipe with `--harness=codex` to use the shared policy for
 * `sandbox_mode`, `approval_policy` and `sandbox_workspace_write`; other configuration keys remain separate
 * targets. Use other rule-file names when that recipe owns the `commands` section in `cook.rules`.
 */
import dev.thecook.codex.Codex

@param(['config'], default = null, doc = 'TOML file whose top-level keys are written into .codex/config.toml')
File config

@param(['hooks'], default = null, doc = 'JSON file whose top-level keys are written into .codex/hooks.json')
File hooks

@param(['rules'], default = null, doc = 'Directory whose files become .codex/rules/<name>')
File rules

@param(['instructions'], default = null, doc = 'Codex-specific instruction text for AGENTS.md')
String instructions

@ingredient(Codex) def codex

if (config) codex.config(new TomlSlurper().parse(config))
if (hooks) codex.hooks(new JsonSlurper().parse(hooks))
if (rules) rules.eachFile { codex.rule(it.name, it.text) }
if (instructions) codex.instruct(instructions)
