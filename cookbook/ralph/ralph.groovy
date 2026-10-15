/**
 * Sets up Ralph TUI as the repository's loop runner: its configuration, a runner and the tracker it takes stories
 * from.
 *
 * ## Before applying
 *
 * - For the `json` tracker, a PRD in Ralph's JSON format at `--prd`. The recipe does not create one.
 * - For `--mode=mise`, mise installed. Bun and Ralph TUI are pinned for it; `mise install` fetches them.
 * - For `--mode=standalone`, `ralph-tui` on the `PATH`.
 * - For the `beads` tracker, `bd` on the `PATH`.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:ralph:0.1.0
 * ./cook dev.thecook:ralph:0.1.0 --tracker=beads --mode=mise --issue-prefix=cook
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `.ralph-tui/config.toml` | Each top-level key | Always |
 * | `.ralph-tui/run` | The whole executable runner | `--mode=standalone` |
 * | `mise.toml` | `tools.bun`, `tools."npm:ralph-tui"` | `--mode=mise` |
 * | `.mise/tasks/ralph/run` | The whole executable runner | `--mode=mise` |
 * | `.beads/config.yaml` | `json` | `--tracker=beads` |
 * | `.beads/config.yaml` | `issue-prefix` | `--tracker=beads` and a non-null `--issue-prefix` |
 * | `.mise/tasks/beads/ready` | The whole executable script | `--tracker=beads` |
 * | `.gitignore` | The section `ignore`: Ralph's local and ephemeral files, and Beads' with `--tracker=beads` | Always |
 *
 * ## After applying
 *
 * - `--mode=standalone`: run `.ralph-tui/run`, with a PRD path or a Beads epic id as its argument.
 * - `--mode=mise`: run `mise install`, then `mise run ralph:run` with the same argument. `mise run beads:ready` lists
 *   the ready Beads tasks.
 * - Ralph's worktrees, iterations, reports and session files stay out of version control.
 *
 * ## With other recipes
 *
 * - `dev.thecook:ralph:0.1.0:prd-story` writes a PRD reduced to one story. Apply it before
 *   `dev.thecook:ralph:0.1.0`, with `--prd` pointing at the same path.
 * - `dev.thecook:ralph:0.1.0:bead-task` adds the task `ralph:bead`, which runs Ralph on one Beads issue.
 *   It needs `--tracker=beads` and `--mode=mise`.
 */
import dev.thecook.beads.Beads
import dev.thecook.git.Git
import dev.thecook.mise.Mise
import dev.thecook.ralph.Ralph

@param(['agent'], default = 'codex', doc = 'The agent Ralph drives, by its Ralph plugin name') String agent
@param(['tracker'], choices = ['json', 'beads'], default = 'json', doc = 'Where Ralph takes its stories from') String tracker
@param(['mode'], choices = ['standalone', 'mise'], default = 'standalone',
       doc = 'Run Ralph through its own runner at .ralph-tui/run, or through the mise task ralph:run') String mode
@param(['prd'], default = 'tasks/prd.json', doc = 'The PRD file, for the json tracker') String prd
@param(['workers'], default = 1, doc = 'Parallel workers; more than one turns parallel mode on') int workers
@param(['max-iterations'], default = 10, doc = 'Iterations before Ralph stops') int maxIterations
@param(['auto-commit'], default = false, doc = 'Commit after each iteration') boolean autoCommit
@param(['headless'], default = false, doc = 'Run without the TUI') boolean headless
@param(['issue-prefix'], default = null, doc = 'The Beads issue prefix, for the beads tracker') String issuePrefix

@ingredient(Ralph) def ralph
@ingredient(Beads) def beads
@ingredient(Mise) def mise
@ingredient(Git) def git

ralph.configure(agent: agent, tracker: tracker, prd: prd, workers: workers,
                maxIterations: maxIterations, autoCommit: autoCommit, headless: headless)
git.ignore(ralph.localFiles + ralph.ephemeralFiles)

if (mode == 'mise') {
  mise.tools(ralph.tools)
  mise.script('ralph:run', ralph.runScript())
} else {
  ralph.installRunner()
}

if (tracker == 'beads') {
  beads.track(issuePrefix)
  git.ignore(beads.localFiles + beads.ephemeralFiles)
  mise.script('beads:ready', beads.readyScript())
}
