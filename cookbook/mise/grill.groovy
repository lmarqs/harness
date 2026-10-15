/**
 * Declares a mise task named `grill` that runs the supplied command.
 *
 * ## Before applying
 *
 * mise and the programs used by the command must be installed to run the task. The command is shell text,
 * stored as written; quote it for the shell when passing `--script`.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:mise:0.1.0:grill --script='./gradlew check'
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `mise.toml` | `tasks.grill`, owned whole, with the supplied command as `run` | Always |
 *
 * ## After applying
 *
 * Run `mise run grill` from the project to run the command.
 *
 * ## With other recipes
 *
 * Apply `dev.thecook:mise:0.1.0:runtime-version` alongside this recipe when the command needs a pinned runtime.
 * Its tool pin and this task are separate targets. Other recipes can add tasks under different names; the last
 * recipe to declare `tasks.grill` owns that task whole.
 */
import dev.thecook.mise.Mise

@param(value = ['script'], doc = 'The command the task runs') String script

@ingredient(Mise) def mise

mise.task('grill', script)
