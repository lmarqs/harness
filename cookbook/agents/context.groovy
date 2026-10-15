/**
 * Seeds `AGENTS.md` as the shared instruction file and connects each selected harness to it.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:agents:0.1.0:context --harness=claude
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `AGENTS.md` | Title, seeded when absent; never owned | Always |
 * | `CLAUDE.md` | The section `instructions`, containing `@AGENTS.md` | `--harness=claude` |
 *
 * ## After applying
 *
 * Add project guidance to `AGENTS.md`; Claude reads it through `CLAUDE.md`. Codex reads `AGENTS.md` natively,
 * so selecting `codex` adds no target.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:agents:0.1.0:permissions` with the same harness selection to render the shared policy.
 * The recipes write separate targets and work in either order.
 */
import dev.thecook.agents.Agents
import dev.thecook.claude.Claude
import dev.thecook.codex.Codex

@param(value = ['harness'], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that read the shared instructions')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex

agents.connect([claude, codex].findAll { it.name in harness })
