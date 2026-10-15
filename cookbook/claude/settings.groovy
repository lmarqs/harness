/**
 * Writes chosen Claude Code settings and instructions from files and arguments you supply.
 *
 * ## Before applying
 *
 * The optional settings input is a JSON file in the destination. It is read as it exists before the invocation;
 * each top-level key becomes one owned setting.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:claude:0.1.0:settings --settings=setup/claude.json --instructions='Run tests before committing.'
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `.claude/settings.json` | Each top-level key of the settings file, owned whole | `--settings` supplies keys |
 * | `CLAUDE.md` | The section `instructions` | `--instructions` supplies text |
 *
 * ## After applying
 *
 * Claude Code reads the project settings and instructions. Apply again with updated inputs to change them;
 * review permission settings against Claude's permission model.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:agents:0.1.0:context` after this recipe to use shared instructions in place of the
 * supplied instructions. Apply `dev.thecook:agents:0.1.0:permissions` after it to use the shared policy for
 * `permissions`; other settings keys remain separate targets. Select `--harness=claude` for either recipe.
 */
import dev.thecook.claude.Claude

@param(['settings'], default = null, doc = 'JSON file whose top-level keys are written into .claude/settings.json')
File settings

@param(['instructions'], default = null, doc = 'Instruction text for CLAUDE.md')
String instructions

@ingredient(Claude) def claude

if (settings) claude.settings(new JsonSlurper().parse(settings))
if (instructions) claude.instruct(instructions)
