/**
 * Seeds `AGENTS.md` with a section template for an agent to fill in, and connects each selected harness to it.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:agents:0.1.0:context
 * ./cook dev.thecook:agents:0.1.0:context --harness=claude
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `AGENTS.md` | Section template, seeded when absent; never owned | Always |
 * | `CLAUDE.md` | The section `instructions`, containing `@AGENTS.md` | `--harness=claude` |
 *
 * ## After applying
 *
 * Have an agent fill in each section of `AGENTS.md`. The line under each heading says what belongs there and
 * stays as a guide for readers. Claude reads the file through `CLAUDE.md`; Codex reads `AGENTS.md` natively, so
 * selecting `codex` adds no target.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:agents:0.1.0:permissions` with the same harness selection to render the shared policy.
 * The recipes write separate targets and work in either order.
 */
import dev.thecook.agents.Agents
import dev.thecook.agents.Claude
import dev.thecook.agents.Codex

@param(value = ['harness'], default = [], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that read the shared instructions')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex

agents.connect([claude, codex].findAll { it.name in harness })
