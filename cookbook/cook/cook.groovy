/**
 * Keeps the engine's local batch records out of version control.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:cook:0.1.0
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `.gitignore` | The section `ignore`: `/.cook/` | Always |
 *
 * ## With other recipes
 *
 * `dev.thecook:cook:0.1.0:setup` supplies this repository's agent setup. Both recipes own the `ignore`
 * section of `.gitignore`; the last recipe's section wins.
 */
import dev.thecook.cook.Cook
import dev.thecook.git.Git

@ingredient(Cook) def cook
@ingredient(Git) def git

git.ignore(cook.localFiles)
