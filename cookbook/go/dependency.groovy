/**
 * Records one requirement in an existing `go.mod` and declares a mise task that tidies and tests the module.
 *
 * ## Before applying
 *
 * The selected module directory must contain `go.mod`. The recipe reads its requirement lines and rejects
 * an existing requirement for the selected module at another version. mise and the project's Go toolchain
 * must be available to run the task.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:go:0.1.0:dependency --module=github.com/google/uuid --version=v1.6.0
 * ./cook dev.thecook:go:0.1.0:dependency --module=github.com/google/uuid --version=v1.6.0 --dir=services/api
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `<dir>/go.mod` | The module's requirement line, kept as written at the requested version or appended when absent | Always |
 * | `mise.toml` | `tasks."dependency:go"`, owned whole, running tidy and tests in `<dir>` | Always |
 *
 * ## After applying
 *
 * Run `mise run dependency:go` to run `go mod tidy` and `go test ./...` in the selected directory. Review
 * the resulting `go.mod` and `go.sum` changes alongside the dependency line.
 *
 * ## With other recipes
 *
 * Distinct module requirement lines compose. Each application declares the same `tasks."dependency:go"`
 * key, so the last recipe in an invocation selects the module directory for the tidy and test task.
 */
import dev.thecook.go.Go
import dev.thecook.mise.Mise

@param(value = ['module'], doc = 'Module path') String module
@param(value = ['version'], doc = 'Module version, such as v1.2.3') String version
@param(value = ['dir'], default = '.', doc = 'The module directory') String dir

@ingredient(Go) def go
@ingredient(Mise) def mise

go.directory = dir
go.require(module, version)
mise.task('dependency:go', go.tidyCommand())
