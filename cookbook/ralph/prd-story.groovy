/**
 * Writes a copy of a JSON PRD reduced to one user story, where Ralph will read it.
 *
 * ## Before applying
 *
 * The source PRD must exist in Ralph's JSON format and contain exactly one `userStories` entry with the supplied
 * `--story` id. The source is read as earlier recipes in the invocation leave it.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:ralph:0.1.0:prd-story --story=US-007 \
 *        dev.thecook:ralph:0.1.0 --prd=.ralph-tui/selected-prd.json --mode=mise
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | The `--selected` path, `.ralph-tui/selected-prd.json` by default | The whole file: the source PRD with `userStories` reduced to the selected story | Always, when the source contains exactly one matching story |
 *
 * ## After applying
 *
 * Run `mise install`, then `mise run ralph:run` to work on the selected story with the configuration above.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:ralph:0.1.0` after this recipe, with `--prd` pointing at the same `--selected` path.
 * `dev.thecook:ralph:0.1.0:bead-task` supplies a task for a Beads issue instead of a JSON story; it uses
 * `dev.thecook:ralph:0.1.0` with `--tracker=beads --mode=mise`.
 */
import dev.thecook.ralph.Ralph

@param(value = ['prd'], default = 'tasks/prd.json', doc = 'The source PRD') String prd
@param(value = ['story'], doc = 'The id of the one story to keep') String story
@param(value = ['selected'], default = '.ralph-tui/selected-prd.json', doc = 'Where the reduced PRD is written') String selected

@ingredient(Ralph) def ralph

ralph.focus(prd, story, selected)
