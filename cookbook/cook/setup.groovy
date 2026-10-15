/**
 * Sets up this repository's shared policy for Claude and Codex, three skills, working instructions and an
 * ignore pattern for Claude's local settings file.
 *
 * ## Before applying
 *
 * Reads `.agents/policy.yaml` when present, or uses the version 1 starter policy when absent.
 * The policy supplies instruction lines and command permissions for both harnesses.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:cook:0.1.0 dev.thecook:cook:0.1.0:setup
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `.agents/policy.yaml` | Starter policy, seeded when absent; never owned | Always |
 * | `AGENTS.md` | Title, seeded when absent; never owned | Always |
 * | `AGENTS.md` | The section `instructions`: working guidance and policy instructions | Always |
 * | `.agents/skills/<name>/` | Whole files for `solid`, `hexagonal-layout`, `effective-java-core` | Always |
 * | `.claude/settings.json` | `permissions` | Always |
 * | `CLAUDE.md` | The section `instructions`: `@AGENTS.md` | Always |
 * | `.claude/skills/<name>` | One relative link per skill | Always |
 * | `.codex/config.toml` | `sandbox_mode`, `approval_policy`, `sandbox_workspace_write` | Always |
 * | `.codex/rules/cook.rules` | The section `commands` | The policy has command rules |
 * | `.gitignore` | The section `ignore`: `/.claude/settings.local.json` | Always |
 *
 * ## After applying
 *
 * Claude reads the shared instructions through `CLAUDE.md` and skills through the relative links.
 * Codex reads `AGENTS.md` and `.agents/skills/` natively. Review the rendered permissions against each
 * harness's permission model; edit the shared policy and apply again to update them.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:cook:0.1.0` with this recipe to ignore the engine's local records.
 * Both recipes own the `ignore` section of `.gitignore`; the last recipe's section wins.
 */
import dev.thecook.agents.Agents
import dev.thecook.claude.Claude
import dev.thecook.codex.Codex
import dev.thecook.git.Git
import dev.thecook.skills.Solid
import dev.thecook.skills.HexagonalLayout
import dev.thecook.skills.EffectiveJavaCore

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex
@ingredient(Git) def git
@ingredient(Solid) def solid
@ingredient(HexagonalLayout) def hexagonal
@ingredient(EffectiveJavaCore) def effectiveJava

def harnesses = [claude, codex]

agents.skills(solid, hexagonal, effectiveJava)
agents.instruct(
  '# Working on the-cook',
  '',
  'Use .agents/skills/solid for implementation and review.',
  'Use .agents/skills/hexagonal-layout for application boundaries and adapters.',
  'Use .agents/skills/effective-java-core for Java and Groovy API design.',
  '',
  'Core contracts and the CLI are Kotlin; cookbook ingredients and recipes are Groovy.',
  'Build and verify changes with ./gradlew check cliBuild.')
agents.instruct(agents.policy().instructions)
agents.permit(harnesses)
agents.connect(harnesses)

git.ignore(claude.localFiles)
