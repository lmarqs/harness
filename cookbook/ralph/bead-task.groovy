/**
 * Declares a mise task that shows one Beads issue and then runs Ralph on it.
 *
 * ## Before applying
 *
 * mise and `bd` must be on the PATH, and the Beads tracker must contain the supplied `--issue`.
 * The `ralph:run` task must use the Beads tracker; apply `dev.thecook:ralph:0.1.0` with
 * `--tracker=beads --mode=mise` to supply it and pin Bun and Ralph TUI.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:ralph:0.1.0 --tracker=beads --mode=mise \
 *        dev.thecook:ralph:0.1.0:bead-task --issue=cook-42
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `mise.toml` | `tasks."ralph:bead"`, owned whole | Always |
 *
 * ## After applying
 *
 * Run `mise install`, then `mise run ralph:bead` to show the issue and run Ralph on it.
 *
 * ## With other recipes
 *
 * `dev.thecook:ralph:0.1.0` supplies the `ralph:run` task with `--tracker=beads --mode=mise`.
 * `dev.thecook:ralph:0.1.0:prd-story` selects one story from a JSON PRD instead; it uses
 * `dev.thecook:ralph:0.1.0` with the JSON tracker pointed at the selected PRD.
 * Other recipes can add tasks under different names; the last recipe declaring `tasks."ralph:bead"` owns it whole.
 */
import dev.thecook.mise.Mise

@param(['issue'], doc = 'The Beads issue to work on') String issue

@ingredient(Mise) def mise

mise.task('ralph:bead', [run: [['bd', 'show', issue], ['mise', 'run', 'ralph:run', issue]]])
